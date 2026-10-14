import asyncio
import base64
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from starlette.requests import Request
from fastapi import HTTPException
import app
from artifact_store import ArtifactStore


def request(data):
    sent = False
    async def receive():
        nonlocal sent
        if sent:
            return {'type': 'http.disconnect'}
        sent = True
        return {'type': 'http.request', 'body': data, 'more_body': False}
    return Request({'type': 'http', 'method': 'POST', 'headers': []}, receive)


class OcrContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = ArtifactStore(Path(self.temp.name))
        self.result = {'text': 'synthetic OCR', 'confidence': 0.9, 'pages': 1, 'lines': 1,
                       'model': 'test', 'layout': [], 'pdfBytes': b'%PDF-synthetic'}

    def tearDown(self):
        self.temp.cleanup()

    def test_v2_returns_small_metadata_and_retryable_binary_without_base64(self):
        with patch.object(app, 'recognize', return_value=self.result.copy()) as recognize, \
                patch.object(app, 'get_artifact_store', return_value=self.store):
            response = asyncio.run(app.ocr_v2(request(b'%PDF-input'), 'application/pdf', None, 'a' * 32))
            payload = json.loads(response.body)
            self.assertNotIn('pdfBase64', payload)
            self.assertNotIn('pdfBytes', payload)
            self.assertEqual(payload['requestId'], 'a' * 32)
            artifact = payload['artifact']
            for _ in range(2):
                with self.store.open(artifact['id'], artifact['capability']) as lease:
                    self.assertEqual(b''.join(lease.chunks()), b'%PDF-synthetic')
            recognize.assert_called_once()

    def test_over_limit_metadata_does_not_leave_artifact(self):
        with patch.object(app, 'recognize', return_value=self.result.copy()), \
                patch.object(app, 'get_artifact_store', return_value=self.store), \
                patch.object(app, 'MAX_METADATA_BYTES', 10):
            with self.assertRaises(HTTPException) as error:
                asyncio.run(app.ocr_v2(request(b'%PDF-input'), 'application/pdf', None, None))
            self.assertEqual(error.exception.status_code, 422)
        self.assertFalse(list(Path(self.temp.name).glob('*.pdf')))

    def test_capability_failure_is_generic_and_does_not_disclose_token(self):
        artifact = self.store.put(b'%PDF-synthetic')
        with patch.object(app, 'get_artifact_store', return_value=self.store):
            with self.assertRaises(HTTPException) as error:
                app.download_artifact(artifact['id'], '0' * 64)
            self.assertEqual(error.exception.status_code, 404)
            self.assertNotIn(artifact['capability'], str(error.exception.detail))

    def test_legacy_recognize_still_has_base64(self):
        from types import SimpleNamespace
        image = Image.new('RGB', (20, 30), 'white')
        output = io.BytesIO(); image.save(output, format='PNG'); image.close()
        result = SimpleNamespace(txts=None, scores=None, boxes=None)
        with patch.object(app, 'get_engine', return_value=lambda *_: result):
            legacy = app.recognize(output.getvalue(), 'image/png', 'test.png')
        self.assertTrue(base64.b64decode(legacy['pdfBase64']).startswith(b'%PDF-'))
        self.assertNotIn('artifact', legacy)

    def test_download_disconnect_releases_active_operation(self):
        artifact = self.store.put(b'%PDF-synthetic')
        with patch.object(app, 'get_artifact_store', return_value=self.store):
            response = app.download_artifact(artifact['id'], artifact['capability'])
            async def send(_):
                raise OSError('synthetic disconnect')
            async def receive():
                return {'type': 'http.disconnect'}
            with self.assertRaises(Exception):
                asyncio.run(response({'type': 'http', 'asgi': {'spec_version': '2.4'}}, receive, send))
        self.assertEqual(self.store.active, {})

    def test_logs_never_include_artifact_capability_or_content(self):
        with patch.object(app, 'recognize', return_value=self.result.copy()), \
                patch.object(app, 'get_artifact_store', return_value=self.store), \
                self.assertLogs('archivo.ocr', level='INFO') as captured:
            response = asyncio.run(app.ocr_v2(request(b'%PDF-private-input'), 'application/pdf', None, 'a' * 32))
        capability = json.loads(response.body)['artifact']['capability']
        self.assertNotIn(capability, str(captured.output))
        self.assertNotIn('synthetic OCR', str(captured.output))
        self.assertNotIn('private-input', str(captured.output))

    def test_legacy_pdf_regeneration_preserves_source_streams(self):
        from test_pdf_overlay import source_pdf, layout_for, image_streams
        original = source_pdf((90, 270))
        payload = json.dumps({'fileBase64': base64.b64encode(original).decode('ascii'),
                              'contentType': 'application/pdf', 'filename': 'test.pdf',
                              'layout': layout_for(original)}).encode('utf-8')
        response = asyncio.run(app.regenerate_pdf(request(payload)))
        self.assertEqual(response.media_type, 'application/pdf')
        self.assertEqual(image_streams(original), image_streams(response.body))


if __name__ == '__main__':
    unittest.main()
