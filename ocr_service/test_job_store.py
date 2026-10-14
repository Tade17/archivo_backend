import tempfile
import unittest
from pathlib import Path
from job_store import JobStore, JobError
from artifact_store import ArtifactStore


class JobStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.artifacts = ArtifactStore(self.temp.name)
        self.store = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.ident, self.cap = 'a' * 32, 'b' * 64

    def tearDown(self):
        self.temp.cleanup()

    def test_duplicate_is_idempotent_and_mismatch_refused(self):
        first = self.store.submit(self.ident, self.cap, b'%PDF-source', 2)
        self.assertEqual(first, self.store.submit(self.ident, self.cap, b'%PDF-source', 2))
        with self.assertRaises(JobError):
            self.store.submit(self.ident, self.cap, b'%PDF-other', 2)

    def test_checkpoint_survives_restart(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 2)
        self.store.checkpoint(self.ident, 0, {'width': 10, 'height': 20, 'blocks': []})
        restored = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.assertEqual(len(restored.checkpoints(self.ident)), 1)
        self.assertEqual(restored.status(self.ident, self.cap)['completedPages'], 1)

    def test_wrong_capability_and_path_are_unavailable(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        for ident, cap in [(self.ident, 'c' * 64), ('../x', self.cap)]:
            with self.assertRaises(JobError):
                self.store.status(ident, cap)

    def test_active_jobs_never_expire_and_cancel_is_terminal(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        self.store.sweep(now=10**12)
        self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'QUEUED')
        self.store.cancel(self.ident, self.cap)
        self.assertEqual(self.store.status(self.ident, self.cap)['state'], 'CANCELLED')

    def test_unified_reservation_blocks_legacy_artifact(self):
        self.artifacts.max_total_bytes = 3000
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        with self.assertRaises(Exception):
            self.artifacts.put(b'%PDF-' + b'x' * 2000)

    def test_malformed_checkpoint_fails_closed_after_restart(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        self.store.checkpoint(self.ident, 0, {'width': 10, 'height': 20, 'blocks': []})
        (Path(self.temp.name) / 'jobs' / self.ident / '000.json').write_text('{}')
        restored = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.assertEqual(restored.status(self.ident, self.cap)['errorCode'], 'OCR_JOB_CORRUPT')

    def test_checkpoint_and_output_limits_do_not_publish_partials(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        with self.assertRaises(JobError):
            self.store.checkpoint(self.ident, 0, {'width': 10, 'height': 20, 'blocks': [], 'texts': ['x' * 2000], 'scores': [0.9]})
        with self.assertRaises(JobError):
            self.store.complete(self.ident, {'pdfBytes': b'%PDF-' + b'x' * 1000})
        self.assertFalse((self.store.root / self.ident / 'output.pdf').exists())

    def test_source_hash_corruption_and_symlink_fail_closed(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        (self.store.root / self.ident / 'source').write_bytes(b'%PDF-mutant')
        restored = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.assertEqual(restored.status(self.ident, self.cap)['errorCode'], 'OCR_JOB_CORRUPT')
        with self.assertRaises(JobError):
            restored.source(self.ident)

    def test_job_count_and_bytes_admission_include_legacy_files(self):
        self.artifacts.max_total_bytes = 2200
        artifact = self.artifacts.put(b'%PDF-' + b'x' * 200)
        with self.assertRaises(JobError) as error:
            self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        self.assertEqual(error.exception.code, 'OCR_JOB_BUSY')
        self.artifacts.discard(artifact['id'], artifact['capability'])
        self.store.max_count = 1
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        with self.assertRaises(JobError):
            self.store.submit('c' * 32, 'd' * 64, b'%PDF-source', 1)

    def test_interrupted_parts_are_reclaimed_and_capabilities_not_persisted(self):
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        part = self.store.root / self.ident / 'output.pdf.part'
        part.write_bytes(b'partial')
        restored = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.assertFalse(part.exists())
        manifest = (restored.root / self.ident / 'job.json').read_text()
        self.assertNotIn(self.cap, manifest)

    def test_storage_failure_exposes_only_static_code(self):
        from unittest.mock import patch
        self.store.submit(self.ident, self.cap, b'%PDF-source', 1)
        with patch.object(self.store, '_save', side_effect=OSError('private/path secret')):
            with self.assertRaises(JobError) as error:
                self.store.cancel(self.ident, self.cap)
        self.assertEqual(str(error.exception), 'OCR_JOB_STORAGE_FAILED')
        self.assertTrue(error.exception.__suppress_context__)

    def test_unpublished_admission_crash_reclaims_only_known_private_files(self):
        folder = self.store.root / self.ident
        folder.mkdir()
        (folder / 'source.part').write_bytes(b'private partial')
        restored = JobStore(self.artifacts, 'f' * 64, max_input=1000, max_output=1000, checkpoint_bytes=1000)
        self.assertFalse(folder.exists())
        self.assertFalse(restored.records)
