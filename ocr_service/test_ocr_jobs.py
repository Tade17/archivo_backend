import asyncio
import hashlib
import io
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from reportlab.pdfgen import canvas
from PIL import Image
import app
from artifact_store import ArtifactStore
from job_store import JobStore, JobError
from test_ocr_contract import request


def pdf_source(pages=3):
    buffer = io.BytesIO()
    writer = canvas.Canvas(buffer, pagesize=(100, 80))
    for number in range(pages):
        writer.drawString(10, 40, f'PAGE {number + 1}')
        writer.showPage()
    writer.save()
    return buffer.getvalue()


def recognition(text='PAGE'):
    return SimpleNamespace(txts=[text], scores=[0.9], boxes=[[[10, 10], [80, 10], [80, 30], [10, 30]]])


class OcrJobsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.artifacts = ArtifactStore(self.temp.name)
        self.store = JobStore(self.artifacts, app.engine_fingerprint())
        self.ident, self.cap = 'a' * 32, 'b' * 64
        self.source = pdf_source()

    def tearDown(self):
        self.temp.cleanup()
        self.assertFalse(app._processing_lock.locked())
        self.assertFalse(app._upload_lock.locked())

    def test_restart_after_checkpoint_never_repeats_successful_inference(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        ident = self.store.claim()
        engine_calls = []
        def first_engine(_):
            engine_calls.append('attempt')
            if len(engine_calls) == 2:
                raise OSError('simulated interruption')
            return recognition('FIRST')
        with patch.object(app, 'get_engine', return_value=first_engine):
            with self.assertRaises(OSError):
                app.recognize(self.source, 'application/pdf', 'doc', True, (self.store, ident))
        self.store.release(ident)
        restored = JobStore(self.artifacts, app.engine_fingerprint())
        second_calls = []
        def second_engine(_):
            second_calls.append('resumed')
            return recognition('REMAINING')
        restored.claim()
        with patch.object(app, 'get_engine', return_value=second_engine):
            result = app.recognize(self.source, 'application/pdf', 'doc', True, (restored, ident))
        restored.complete(ident, result)
        restored.release(ident)
        self.assertEqual(len(second_calls), 2)
        self.assertEqual(restored.status(ident, self.cap)['completedPages'], 3)
        metadata = restored.result(ident, self.cap)
        self.assertIn('FIRST', metadata['text'])
        self.assertNotIn('pdfBase64', metadata)
        for _ in range(2):
            with restored.open(ident, self.cap) as stream:
                self.assertEqual(hashlib.sha256(b''.join(stream.chunks())).hexdigest(), metadata['artifact']['sha256'])
        self.assertEqual(len(second_calls), 2)
        self.assertFalse(restored.active)

    def test_lifespan_stops_worker_and_no_second_worker_allowed(self):
        with patch.object(app, 'get_job_store', return_value=self.store):
            async def run():
                async with app.lifespan(app.app):
                    self.assertTrue(app.health()['jobsReady'])
                    with self.assertRaises(JobError):
                        app.JobWorker(self.store).start()
                self.assertFalse(app.app.state.job_worker.thread.is_alive())
            asyncio.run(run())
        self.assertFalse(self.store.worker_started)

    def test_authenticated_api_submit_status_result_cancel(self):
        with patch.object(app, 'get_job_store', return_value=self.store):
            status = asyncio.run(app.submit_job(self.ident, request(self.source), self.cap,
                                                hashlib.sha256(self.source).hexdigest(), app.engine_fingerprint()))
            self.assertEqual(status['pages'], 3)
            self.assertEqual(app.job_status(self.ident, self.cap)['state'], 'QUEUED')
            with self.assertRaises(app.HTTPException) as error:
                app.job_result(self.ident, self.cap)
            self.assertEqual(error.exception.status_code, 409)
            self.assertEqual(app.cancel_job(self.ident, self.cap)['state'], 'CANCELLED')
            with self.assertRaises(app.HTTPException):
                app.job_status(self.ident, 'c' * 64)

    def test_hash_and_fingerprint_mismatch_have_safe_codes(self):
        for digest, fingerprint in [('c' * 64, app.engine_fingerprint()), (hashlib.sha256(self.source).hexdigest(), 'd' * 64)]:
            with self.assertRaises(app.HTTPException) as error:
                asyncio.run(app.submit_job(self.ident, request(self.source), self.cap, digest, fingerprint))
            self.assertNotIn(self.cap, str(error.exception.detail))
        self.assertFalse(self.store.records)

    def test_legacy_overload_rejected_without_unbounded_wait(self):
        app._processing_lock.acquire()
        try:
            with self.assertRaises(app.HTTPException) as error:
                asyncio.run(app.ocr(request(self.source), 'application/pdf', None))
            self.assertEqual(error.exception.status_code, 503)
        finally:
            app._processing_lock.release()

    def test_worker_cancel_midpage_does_not_publish_and_releases_resources(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        entered, resume = threading.Event(), threading.Event()
        def engine(_):
            entered.set()
            if not resume.wait(5):
                raise TimeoutError()
            return recognition()
        worker = app.JobWorker(self.store)
        with patch.object(app, 'get_engine', return_value=engine):
            worker.start()
            try:
                self.assertTrue(entered.wait(5))
                self.store.cancel(self.ident, self.cap)
                self.store.sweep(now=10**12)
                self.assertIn(self.ident, self.store.records)
            finally:
                resume.set()
                worker.close()
        self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'CANCELLED')
        self.assertFalse(self.store.processing)
        self.assertFalse((Path(self.temp.name) / 'jobs' / self.ident / 'output.pdf').exists())

    def test_completed_restart_download_and_expiry_lease_protection(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        self.store.claim()
        with patch.object(app, 'get_engine', return_value=lambda _: recognition()):
            result = app.recognize(self.source, 'application/pdf', 'doc', True, (self.store, self.ident))
        self.store.complete(self.ident, result)
        self.store.release(self.ident)
        restored = JobStore(self.artifacts, app.engine_fingerprint())
        self.assertIsNone(restored.claim())
        with restored.open(self.ident, self.cap):
            restored.sweep(now=10**12)
            self.assertIn(self.ident, restored.records)
        restored.sweep(now=10**12)
        self.assertNotIn(self.ident, restored.records)

    def test_config_change_refuses_resume_without_inference(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        restored = JobStore(self.artifacts, 'd' * 64)
        self.assertEqual(restored.status(self.ident, self.cap)['errorCode'], 'OCR_JOB_CONFIG_CHANGED')
        self.assertIsNone(restored.claim())

    def test_shutdown_after_first_checkpoint_resumes_remaining_pages(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        worker = app.JobWorker(self.store)
        original_checkpoint = self.store.checkpoint
        reached = threading.Event()
        def checkpoint(*args):
            original_checkpoint(*args)
            worker.stop.set()
            reached.set()
        with patch.object(app, 'get_engine', return_value=lambda _: recognition()), patch.object(self.store, 'checkpoint', side_effect=checkpoint):
            worker.start()
            try:
                self.assertTrue(reached.wait(5))
            finally:
                worker.close()
        self.assertEqual(self.store.status(self.ident, self.cap)['completedPages'], 1)
        self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'QUEUED')
        self.assertFalse(self.store.processing)

    def test_cancellation_retains_legacy_admission_until_thread_finishes(self):
        entered, resume = threading.Event(), threading.Event()
        def recognize(*_):
            entered.set()
            resume.wait(5)
            return {'text': ''}
        async def run():
            with patch.object(app, 'recognize', side_effect=recognize):
                task = asyncio.create_task(app.ocr(request(self.source), 'application/pdf', None))
                await asyncio.to_thread(entered.wait, 5)
                task.cancel()
                await asyncio.sleep(0)
                self.assertTrue(app._processing_lock.locked())
                task.cancel()
                await asyncio.sleep(0)
                self.assertTrue(app._processing_lock.locked())
                resume.set()
                with self.assertRaises(asyncio.CancelledError):
                    await task
        try:
            asyncio.run(run())
        finally:
            resume.set()

    def test_acknowledgement_after_publication_protects_active_lease_and_frees_quota(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        self.store.claim()
        with patch.object(app, 'get_engine', return_value=lambda _: recognition()):
            result = app.recognize(self.source, 'application/pdf', 'doc', True, (self.store, self.ident))
        self.store.complete(self.ident, result)
        self.store.release(self.ident)
        with self.store.open(self.ident, self.cap) as lease:
            with self.assertRaises(JobError) as error:
                self.store.acknowledge(self.ident, self.cap)
            self.assertEqual(error.exception.code, 'OCR_JOB_BUSY')
            self.assertTrue(b''.join(lease.chunks()).startswith(b'%PDF-'))
            self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'COMPLETED')
        with patch.object(self.store, '_save', side_effect=OSError('synthetic write failure')):
            with self.assertRaises(JobError):
                self.store.acknowledge(self.ident, self.cap)
        self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'COMPLETED')
        with patch.object(app, 'get_job_store', return_value=self.store):
            with self.assertRaises(app.HTTPException) as error:
                app.acknowledge_job(self.ident, 'c' * 64)
            self.assertEqual(error.exception.status_code, 404)
            self.assertEqual(app.acknowledge_job(self.ident, self.cap).status_code, 204)
            with self.assertRaises(app.HTTPException) as error:
                app.acknowledge_job(self.ident, self.cap)
            self.assertEqual(error.exception.status_code, 404)
        self.assertEqual(self.store.usage(), (0, 0))
        self.assertFalse((self.store.root / self.ident).exists())

    def test_ack_cleanup_failure_retries_durable_private_tombstone(self):
        self.store.submit(self.ident, self.cap, self.source, 3)
        self.store.claim()
        with patch.object(app, 'get_engine', return_value=lambda _: recognition()):
            result = app.recognize(self.source, 'application/pdf', 'doc', True, (self.store, self.ident))
        self.store.complete(self.ident, result)
        self.store.release(self.ident)
        with patch.object(self.store, 'sweep', side_effect=JobError('OCR_JOB_STORAGE_FAILED')):
            self.store.acknowledge(self.ident, self.cap)
        self.assertEqual(self.store.records[self.ident]['state'], 'ACKNOWLEDGED')
        restored = JobStore(self.artifacts, app.engine_fingerprint())
        self.assertFalse(restored.records)
