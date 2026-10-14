"""Bounded, capability-protected PDF cache for the single OCR service worker."""
import hashlib
import hmac
import json
import math
import os
import re
import secrets
import stat
import threading
import time
from pathlib import Path

ID_PATTERN = re.compile(r'^[a-f0-9]{32}$')
TOKEN_PATTERN = re.compile(r'^[a-f0-9]{64}$')


class ArtifactError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class ArtifactLease:
    def __init__(self, store, ident, handle, size):
        self.store, self.ident, self.handle, self.size = store, ident, handle, size
        self.closed = False

    def chunks(self):
        while data := self.handle.read(256 * 1024):
            yield data

    def close(self):
        with self.store.lock:
            if not self.closed:
                self.closed = True
                self.handle.close()
                self.store.active[self.ident] -= 1
                if self.store.active[self.ident] == 0:
                    del self.store.active[self.ident]

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class ArtifactStore:
    def __init__(self, root, max_bytes=128 * 1024 * 1024,
                 max_total_bytes=512 * 1024 * 1024, max_count=32, ttl_seconds=3600):
        candidate = Path(root).absolute()
        if any(path.is_symlink() for path in (candidate, *candidate.parents)):
            raise ArtifactError('cache_invalid')
        candidate.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.root = candidate.resolve()
        self.max_bytes = min(int(max_bytes), 128 * 1024 * 1024)
        self.max_total_bytes = min(int(max_total_bytes), 512 * 1024 * 1024)
        self.max_count = min(int(max_count), 32)
        self.ttl = min(int(ttl_seconds), 3600)
        if min(self.max_bytes, self.max_total_bytes, self.max_count, self.ttl) <= 0:
            raise ArtifactError('cache_invalid')
        self.lock = threading.RLock()
        self.active = {}
        self.sweep()

    def _path(self, ident, suffix):
        if not isinstance(ident, str) or not ID_PATTERN.fullmatch(ident):
            raise ArtifactError('artifact_unavailable')
        path = self.root / (ident + suffix)
        if path.parent != self.root or path.is_symlink():
            raise ArtifactError('artifact_unavailable')
        return path

    def _record(self, ident):
        path = self._path(ident, '.json')
        try:
            fd = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
            with os.fdopen(fd, 'rb') as handle:
                info = os.fstat(handle.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_size > 4096:
                    raise ArtifactError('artifact_unavailable')
                record = json.loads(handle.read(4097))
            if (not isinstance(record, dict) or record.get('id') != ident
                    or not TOKEN_PATTERN.fullmatch(record.get('capabilityHash', ''))
                    or not TOKEN_PATTERN.fullmatch(record.get('sha256', ''))
                    or not isinstance(record.get('sizeBytes'), int)
                    or not 5 <= record['sizeBytes'] <= self.max_bytes
                    or not isinstance(record.get('expiresAt'), (int, float))
                    or not math.isfinite(record['expiresAt'])):
                raise ArtifactError('artifact_unavailable')
            return record
        except (OSError, ValueError, TypeError) as error:
            raise ArtifactError('artifact_unavailable') from error

    def sweep(self):
        with self.lock:
            now = time.time()
            for path in self.root.iterdir():
                if path.is_symlink() or not path.is_file():
                    continue
                if path.suffix == '.json' and ID_PATTERN.fullmatch(path.stem):
                    try:
                        record = self._record(path.stem)
                    except ArtifactError:
                        continue
                    if record['expiresAt'] <= now and not self.active.get(path.stem):
                        self._path(path.stem, '.pdf').unlink(missing_ok=True)
                        path.unlink(missing_ok=True)
                elif path.suffix in {'.part', '.pdf'} and not self.active.get(path.stem):
                    # Crash leftovers stay bounded and become reclaimable after the same TTL.
                    if not (self.root / (path.stem + '.json')).exists() and now - path.stat().st_mtime >= self.ttl:
                        path.unlink(missing_ok=True)

    def put(self, data):
        if not isinstance(data, bytes) or not data.startswith(b'%PDF-') or len(data) > self.max_bytes:
            raise ArtifactError('artifact_limit')
        with self.lock:
            self.sweep()
            files = [path for path in self.root.iterdir() if path.is_file() and not path.is_symlink()
                     and (path.suffix == '.pdf' or path.suffix == '.part' and not path.name.endswith('.json.part'))]
            # Interrupted writes consume admission quota too, not just completed PDFs.
            reserved_ids = {path.name[:32] for path in self.root.iterdir()
                            if ID_PATTERN.fullmatch(path.name[:32])}
            if len(reserved_ids) >= self.max_count or sum(path.stat().st_size for path in files) + len(data) > self.max_total_bytes:
                raise ArtifactError('cache_busy')
            ident, capability = secrets.token_hex(16), secrets.token_hex(32)
            output, manifest = self._path(ident, '.pdf'), self._path(ident, '.json')
            if output.exists() or manifest.exists():
                raise ArtifactError('cache_busy')
            parts = [self._path(ident, '.part'), self._path(ident, '.json.part')]
            record = {'id': ident, 'capabilityHash': hashlib.sha256(capability.encode('ascii')).hexdigest(),
                      'sizeBytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                      'expiresAt': time.time() + self.ttl}
            try:
                for path, body in zip(parts, [data, json.dumps(record).encode('utf-8')]):
                    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0), 0o600)
                    with os.fdopen(fd, 'wb') as handle:
                        handle.write(body)
                        handle.flush()
                        os.fsync(handle.fileno())
                os.replace(parts[0], output)
                os.replace(parts[1], manifest)
            except OSError as error:
                for path in (*parts, output, manifest):
                    path.unlink(missing_ok=True)
                raise ArtifactError('cache_write_failed') from error
            return {'id': ident, 'capability': capability, 'sizeBytes': len(data), 'sha256': record['sha256']}

    def open(self, ident, capability):
        if not isinstance(capability, str) or not TOKEN_PATTERN.fullmatch(capability):
            raise ArtifactError('artifact_unavailable')
        with self.lock:
            record = self._record(ident)
            if not hmac.compare_digest(record['capabilityHash'], hashlib.sha256(capability.encode('ascii')).hexdigest()):
                raise ArtifactError('artifact_unavailable')
            if record['expiresAt'] <= time.time():
                raise ArtifactError('artifact_unavailable')
            try:
                fd = os.open(self._path(ident, '.pdf'), os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
                handle = os.fdopen(fd, 'rb')
                info = os.fstat(handle.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_size != record['sizeBytes']:
                    handle.close()
                    raise ArtifactError('artifact_unavailable')
            except OSError as error:
                raise ArtifactError('artifact_unavailable') from error
            self.active[ident] = self.active.get(ident, 0) + 1
            return ArtifactLease(self, ident, handle, record['sizeBytes'])

    def discard(self, ident, capability):
        with self.lock:
            with self.open(ident, capability):
                pass
            if self.active.get(ident):
                raise ArtifactError('cache_busy')
            self._path(ident, '.pdf').unlink(missing_ok=True)
            self._path(ident, '.json').unlink(missing_ok=True)
