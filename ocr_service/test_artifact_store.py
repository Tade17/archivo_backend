import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from artifact_store import ArtifactStore, ArtifactError


class ArtifactStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.store = ArtifactStore(self.root, max_bytes=100, max_total_bytes=150, max_count=2, ttl_seconds=10)
        self.pdf = b'%PDF-' + b'x' * 55

    def tearDown(self):
        self.temp.cleanup()

    def test_capability_and_id_are_independent_and_download_is_repeatable(self):
        artifact = self.store.put(self.pdf)
        self.assertEqual(len(artifact['id']), 32)
        self.assertEqual(len(artifact['capability']), 64)
        for _ in range(2):
            with self.store.open(artifact['id'], artifact['capability']) as lease:
                self.assertEqual(b''.join(lease.chunks()), self.pdf)
        self.assertNotIn(artifact['capability'], next(self.root.glob('*.json')).read_text())

    def test_wrong_capability_and_traversal_fail_closed(self):
        artifact = self.store.put(self.pdf)
        for ident, capability in [(artifact['id'], '0' * 64), ('../outside', artifact['capability']),
                                  (artifact['id'], artifact['id']), ('a' * 32, artifact['capability'])]:
            with self.subTest(ident=ident), self.assertRaises(ArtifactError):
                self.store.open(ident, capability)

    def test_store_survives_recreation(self):
        artifact = self.store.put(self.pdf)
        restarted = ArtifactStore(self.root, max_bytes=100, max_total_bytes=150, max_count=2, ttl_seconds=10)
        with restarted.open(artifact['id'], artifact['capability']) as lease:
            self.assertEqual(b''.join(lease.chunks()), self.pdf)

    def test_expired_active_download_is_not_deleted(self):
        with patch('artifact_store.time.time', return_value=100):
            artifact = self.store.put(self.pdf)
            lease = self.store.open(artifact['id'], artifact['capability'])
        with patch('artifact_store.time.time', return_value=111):
            self.store.sweep()
            self.assertTrue((self.root / (artifact['id'] + '.pdf')).exists())
            self.assertEqual(b''.join(lease.chunks()), self.pdf)
            lease.close()
            self.store.sweep()
            self.assertFalse((self.root / (artifact['id'] + '.pdf')).exists())

    def test_quota_and_count_reject_without_partial_files(self):
        self.store.put(self.pdf)
        self.store.put(self.pdf)
        with self.assertRaises(ArtifactError):
            self.store.put(self.pdf)
        self.assertEqual(len(list(self.root.glob('*.pdf'))), 2)
        self.assertFalse(list(self.root.glob('*.part')))

    def test_size_and_signature_are_bounded(self):
        for data in (b'bad PDF', b'%PDF-' + b'x' * 101):
            with self.assertRaises(ArtifactError):
                self.store.put(data)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_symlink_is_never_followed(self):
        artifact = self.store.put(self.pdf)
        path = self.root / (artifact['id'] + '.pdf')
        path.unlink()
        outside = self.root.parent / ('outside-' + artifact['id'])
        outside.write_bytes(self.pdf)
        try:
            path.symlink_to(outside)
            with self.assertRaises(ArtifactError):
                self.store.open(artifact['id'], artifact['capability'])
            self.assertEqual(outside.read_bytes(), self.pdf)
        finally:
            outside.unlink()

    def test_failed_publication_removes_partial_files(self):
        with patch('artifact_store.os.replace', side_effect=OSError('synthetic disk failure')):
            with self.assertRaises(ArtifactError):
                self.store.put(self.pdf)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_total_byte_quota_includes_interrupted_writes(self):
        store = ArtifactStore(self.root, max_bytes=100, max_total_bytes=100, max_count=10, ttl_seconds=10)
        (self.root / ('a' * 32 + '.part')).write_bytes(b'x' * 60)
        with self.assertRaises(ArtifactError) as error:
            store.put(self.pdf)
        self.assertEqual(error.exception.code, 'cache_busy')

    def test_expired_artifact_frees_admission_quota(self):
        with patch('artifact_store.time.time', return_value=100):
            artifact = self.store.put(self.pdf)
        with patch('artifact_store.time.time', return_value=111):
            self.store.put(self.pdf)
            self.assertFalse((self.root / (artifact['id'] + '.pdf')).exists())

    def test_capability_comparison_uses_constant_time_primitive(self):
        artifact = self.store.put(self.pdf)
        import hmac
        with patch('artifact_store.hmac.compare_digest', wraps=hmac.compare_digest) as compare:
            with self.store.open(artifact['id'], artifact['capability']):
                pass
            compare.assert_called_once()


if __name__ == '__main__':
    unittest.main()
