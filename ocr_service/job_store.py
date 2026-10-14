"""Single-process durable jobs with bounded reservations and private opaque paths."""
import hashlib
import hmac
import json
import math
import os
import re
import stat
import threading
import time
from pathlib import Path
from functools import wraps
from artifact_store import ID_PATTERN, TOKEN_PATTERN, ArtifactLease, ArtifactError


class JobError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def safe_storage(function):
    @wraps(function)
    def invoke(*args, **kwargs):
        try:
            return function(*args, **kwargs)
        except JobError:
            raise
        except (OSError, ValueError, KeyError, TypeError) as error:
            # Never leak local paths, OCR payload fragments or chained I/O exceptions.
            raise JobError('OCR_JOB_STORAGE_FAILED') from None
    return invoke


class JobStore:
    @safe_storage
    def __init__(self, artifacts, fingerprint, max_input=25 * 1024 * 1024,
                 max_output=128 * 1024 * 1024, checkpoint_bytes=16 * 1024 * 1024,
                 max_count=8, ttl=3600):
        self.artifacts = artifacts
        self.lock = artifacts.lock
        self.root = artifacts.root / 'jobs'
        if self.root.is_symlink():
            raise JobError('OCR_JOB_CORRUPT')
        self.root.mkdir(mode=0o700, exist_ok=True)
        self.fingerprint = fingerprint
        self.max_input = min(max_input, 25 * 1024 * 1024)
        self.max_output = min(max_output, artifacts.max_bytes)
        self.checkpoint_bytes = min(checkpoint_bytes, 16 * 1024 * 1024)
        self.max_count = min(max_count, 8)
        self.ttl = min(ttl, 3600)
        if min(self.max_input, self.max_output, self.checkpoint_bytes, self.max_count, self.ttl) <= 0:
            raise JobError('OCR_JOB_LIMIT')
        self.records = {}
        self.active = {}
        self.processing = set()
        self.wake = threading.Event()
        self._recover()
        artifacts.external_usage = self.usage
        self.sweep()

    def _path(self, ident, name):
        if not isinstance(ident, str) or not ID_PATTERN.fullmatch(ident):
            raise JobError('OCR_JOB_UNAVAILABLE')
        folder = self.root / ident
        path = folder / name
        if folder.is_symlink() or path.is_symlink() or path.parent != folder:
            raise JobError('OCR_JOB_UNAVAILABLE')
        return path

    def _read(self, path, limit):
        fd = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
        with os.fdopen(fd, 'rb') as stream:
            info = os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
                raise JobError('OCR_JOB_CORRUPT')
            return stream.read(limit + 1)

    def _write(self, path, data):
        partial = path.with_name(path.name + '.part')
        if partial.is_symlink():
            raise JobError('OCR_JOB_CORRUPT')
        fd = os.open(partial, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, 'O_NOFOLLOW', 0), 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(partial, path)
        fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def _save(self, record):
        body = json.dumps(record, allow_nan=False).encode()
        if len(body) > 4096:
            raise JobError('OCR_JOB_LIMIT')
        self._write(self._path(record['id'], 'job.json'), body)

    def _recover(self):
        folders = []
        for folder in self.root.iterdir():
            folders.append(folder)
            if len(folders) > self.max_count:
                raise JobError('OCR_JOB_LIMIT')
        if len(folders) > self.max_count:
            raise JobError('OCR_JOB_LIMIT')
        for folder in folders:
            if folder.is_symlink() or not folder.is_dir() or not ID_PATTERN.fullmatch(folder.name):
                raise JobError('OCR_JOB_CORRUPT')
            try:
                entries = []
                for entry in folder.iterdir():
                    entries.append(entry)
                    if len(entries) > 210 or entry.is_symlink() or not entry.is_file():
                        raise JobError('OCR_JOB_CORRUPT')
                    name = entry.name.removesuffix('.part')
                    if name not in {'job.json', 'source', 'output.pdf', 'result.json'} and not re.fullmatch(r'[0-9]{3}\.json', name):
                        raise JobError('OCR_JOB_CORRUPT')
                if not (folder / 'job.json').exists() and all(p.name in {'source', 'source.part', 'job.json.part'} for p in entries):
                    # Unpublished submission or final empty acknowledgement directory.
                    for entry in entries:
                        entry.unlink()
                    folder.rmdir()
                    continue
                for partial in folder.glob('*.part'):
                    if partial.is_symlink() or not partial.is_file():
                        raise JobError('OCR_JOB_CORRUPT')
                    partial.unlink()
                record = json.loads(self._read(self._path(folder.name, 'job.json'), 4096))
                if (record['id'] != folder.name or not TOKEN_PATTERN.fullmatch(record['capabilityHash'])
                        or not TOKEN_PATTERN.fullmatch(record['sourceHash'])
                        or not TOKEN_PATTERN.fullmatch(record['fingerprint'])
                        or type(record['pages']) is not int or not 1 <= record['pages'] <= 100
                        or type(record['inputBytes']) is not int or not 1 <= record['inputBytes'] <= self.max_input
                        or record['state'] not in {'QUEUED', 'RUNNING', 'COMPLETED', 'ERROR', 'CANCELLED', 'ACKNOWLEDGED'}
                        or not math.isfinite(record['updatedAt'])):
                    raise JobError('OCR_JOB_CORRUPT')
                self.records[folder.name] = record
                if record['state'] == 'ACKNOWLEDGED':
                    continue
                source = self._read(self._path(folder.name, 'source'), self.max_input)
                if len(source) != record['inputBytes'] or hashlib.sha256(source).hexdigest() != record['sourceHash']:
                    raise JobError('OCR_JOB_CORRUPT')
                self.checkpoints(folder.name)
                if not self._path(folder.name, 'result.json').exists():
                    self._path(folder.name, 'output.pdf').unlink(missing_ok=True)
                elif record['state'] == 'COMPLETED':
                    self._result(folder.name)
                if record['fingerprint'] != self.fingerprint:
                    raise JobError('OCR_JOB_CONFIG_CHANGED')
                if record['state'] == 'RUNNING':
                    record.update(state='QUEUED', phase='QUEUED')
                    self._save(record)
                # Publication after a crash is recovered without running inference/export again.
                if record['state'] == 'QUEUED' and self._path(folder.name, 'result.json').exists():
                    result = self._result(folder.name)
                    record.update(state='COMPLETED', phase='COMPLETED', completedPages=result['pages'])
                    self._save(record)
            except (OSError, ValueError, TypeError, KeyError, JobError) as error:
                if folder.name not in self.records:
                    # Unknown ownership cannot safely be reclaimed or resumed.
                    raise JobError('OCR_JOB_CORRUPT') from error
                record = self.records[folder.name]
                record.update(state='ERROR', phase='ERROR', errorCode=error.code if isinstance(error, JobError) else 'OCR_JOB_CORRUPT')
                self._save(record)

    def usage(self):
        # Full reservations include partial output/checkpoint writes, not only committed bytes.
        reserved = 0
        for ident, record in self.records.items():
            actual = sum(p.lstat().st_size for p in (self.root / ident).iterdir())
            reserved += max(self.max_output + self.checkpoint_bytes + record['inputBytes'], actual)
        return reserved, len(self.records)

    @safe_storage
    def submit(self, ident, capability, source, pages):
        if not isinstance(capability, str) or not TOKEN_PATTERN.fullmatch(capability):
            raise JobError('OCR_JOB_UNAVAILABLE')
        self._path(ident, 'source')
        if not isinstance(source, bytes) or not 1 <= len(source) <= self.max_input or type(pages) is not int or not 1 <= pages <= 100:
            raise JobError('OCR_JOB_LIMIT')
        digest = hashlib.sha256(source).hexdigest()
        with self.lock:
            self.sweep()
            if ident in self.records:
                record = self._auth(ident, capability)
                if record['sourceHash'] != digest or record['fingerprint'] != self.fingerprint or record['pages'] != pages:
                    raise JobError('OCR_JOB_CONFLICT')
                return self._status(record)
            reserved, count = self.usage()
            files = [p for p in self.artifacts.root.iterdir() if p.is_file() and not p.is_symlink()
                     and (p.suffix == '.pdf' or p.suffix == '.part' and not p.name.endswith('.json.part'))]
            artifact_ids = {p.name[:32] for p in self.artifacts.root.iterdir() if ID_PATTERN.fullmatch(p.name[:32])}
            if (count >= self.max_count or count + len(artifact_ids) >= self.artifacts.max_count
                    or reserved + len(source) + self.max_output + self.checkpoint_bytes + sum(p.stat().st_size for p in files) > self.artifacts.max_total_bytes):
                raise JobError('OCR_JOB_BUSY')
            folder = self.root / ident
            if folder.exists():
                raise JobError('OCR_JOB_CONFLICT')
            folder.mkdir(mode=0o700)
            record = {'id': ident, 'capabilityHash': hashlib.sha256(capability.encode()).hexdigest(),
                      'sourceHash': digest, 'fingerprint': self.fingerprint, 'inputBytes': len(source),
                      'pages': pages, 'completedPages': 0, 'state': 'QUEUED', 'phase': 'QUEUED',
                      'errorCode': None, 'updatedAt': time.time()}
            try:
                self._write(self._path(ident, 'source'), source)
                self._save(record)
            except Exception:
                for path in folder.iterdir():
                    if path.is_file() and not path.is_symlink():
                        path.unlink()
                folder.rmdir()
                raise
            self.records[ident] = record
            self.wake.set()
            return self._status(record)

    def _auth(self, ident, capability):
        self._path(ident, 'job.json')
        record = self.records.get(ident)
        if (not record or not isinstance(capability, str) or not TOKEN_PATTERN.fullmatch(capability)
                or not hmac.compare_digest(record['capabilityHash'], hashlib.sha256(capability.encode()).hexdigest())):
            raise JobError('OCR_JOB_UNAVAILABLE')
        return record

    def _status(self, record):
        return {key: record[key] for key in ('id', 'state', 'phase', 'pages', 'completedPages', 'errorCode')}

    @safe_storage
    def status(self, ident, capability):
        with self.lock:
            self.sweep()
            return self._status(self._auth(ident, capability))

    @safe_storage
    def claim(self):
        with self.lock:
            for record in self.records.values():
                if record['state'] == 'QUEUED':
                    record.update(state='RUNNING', phase='RECOGNIZING')
                    self._save(record)
                    self.processing.add(record['id'])
                    return record['id']

    def release(self, ident):
        with self.lock:
            self.processing.discard(ident)

    @safe_storage
    def source(self, ident):
        source = self._read(self._path(ident, 'source'), self.max_input)
        if hashlib.sha256(source).hexdigest() != self.records[ident]['sourceHash']:
            raise JobError('OCR_JOB_CORRUPT')
        return source

    @safe_storage
    def checkpoints(self, ident):
        record = self.records[ident]
        folder = self.root / ident
        files = sorted(folder.glob('[0-9][0-9][0-9].json'))
        if len(files) > record['pages'] or sum(p.lstat().st_size for p in files) > self.checkpoint_bytes:
            raise JobError('OCR_JOB_CORRUPT')
        checkpoints = []
        for index, path in enumerate(files):
            if path.name != f'{index:03}.json':
                raise JobError('OCR_JOB_CORRUPT')
            page = json.loads(self._read(self._path(ident, path.name), min(256 * 1024, self.checkpoint_bytes)))
            self._validate_page(page)
            checkpoints.append(page)
        record['completedPages'] = len(checkpoints)
        return checkpoints

    def _validate_page(self, page):
        from searchable_pdf import validate_layout_dimensions
        if not isinstance(page, dict):
            raise JobError('OCR_JOB_CORRUPT')
        try:
            width, height = page['width'], page['height']
            if min(width, height) <= 0:
                raise ValueError()
            if not isinstance(width, (int, float)) or not isinstance(height, (int, float)) or not math.isfinite(width * height) or width * height > 250_000_000:
                raise ValueError()
            validate_layout_dimensions([(width, height)], [page])
            if 'texts' in page or 'scores' in page:
                texts, scores = page['texts'], page['scores']
                if (not isinstance(texts, list) or not isinstance(scores, list) or len(texts) > 10000
                        or len(scores) > 10000 or any(not isinstance(t, str) for t in texts)
                        or any(not isinstance(v, (int, float)) or not math.isfinite(v) or not 0 <= v <= 1 for v in scores)):
                    raise ValueError()
        except (KeyError, TypeError, ValueError, OverflowError) as error:
            raise JobError('OCR_JOB_CORRUPT') from error

    @safe_storage
    def checkpoint(self, ident, index, page):
        self._validate_page(page)
        body = json.dumps(page, allow_nan=False).encode()
        with self.lock:
            record = self.records[ident]
            if record['state'] == 'CANCELLED':
                raise JobError('OCR_JOB_CANCELLED')
            existing = self.checkpoints(ident)
            if index != len(existing) or index >= record['pages'] or len(body) > 256 * 1024:
                raise JobError('OCR_JOB_LIMIT')
            if sum(len(json.dumps(p, allow_nan=False).encode()) for p in existing) + len(body) > self.checkpoint_bytes:
                raise JobError('OCR_JOB_LIMIT')
            self._write(self._path(ident, f'{index:03}.json'), body)
            record.update(completedPages=index + 1, updatedAt=time.time())
            self._save(record)

    def exporting(self, ident):
        with self.lock:
            if self.records[ident]['state'] == 'CANCELLED':
                raise JobError('OCR_JOB_CANCELLED')
            self.records[ident]['phase'] = 'EXPORTING'
            self._save(self.records[ident])

    @safe_storage
    def complete(self, ident, result):
        pdf = result.pop('pdfBytes')
        if not isinstance(pdf, bytes) or not pdf.startswith(b'%PDF-') or len(pdf) > self.max_output:
            raise JobError('OCR_JOB_LIMIT')
        result['artifact'] = {'id': ident, 'sizeBytes': len(pdf), 'sha256': hashlib.sha256(pdf).hexdigest()}
        body = json.dumps(result, allow_nan=False).encode()
        if len(body) > min(self.checkpoint_bytes, 16 * 1024 * 1024 - 1024):
            raise JobError('OCR_JOB_LIMIT')
        with self.lock:
            record = self.records[ident]
            if record['state'] == 'CANCELLED':
                raise JobError('OCR_JOB_CANCELLED')
            # Checkpoints and result coexist; reserve their combined actual bytes.
            checkpoint_size = sum(p.stat().st_size for p in (self.root / ident).glob('[0-9][0-9][0-9].json'))
            if checkpoint_size + len(body) > self.checkpoint_bytes:
                raise JobError('OCR_JOB_LIMIT')
            self._write(self._path(ident, 'output.pdf'), pdf)
            self._write(self._path(ident, 'result.json'), body)
            record.update(state='COMPLETED', phase='COMPLETED', updatedAt=time.time())
            self._save(record)

    def _result(self, ident):
        result = json.loads(self._read(self._path(ident, 'result.json'), self.checkpoint_bytes))
        pdf = self._read(self._path(ident, 'output.pdf'), self.max_output)
        artifact = result['artifact']
        if (artifact['id'] != ident or artifact['sizeBytes'] != len(pdf)
                or artifact['sha256'] != hashlib.sha256(pdf).hexdigest() or not pdf.startswith(b'%PDF-')
                or result['pages'] != self.records[ident]['pages']):
            raise JobError('OCR_JOB_CORRUPT')
        return result

    @safe_storage
    def result(self, ident, capability):
        with self.lock:
            self.sweep()
            record = self._auth(ident, capability)
            if record['state'] != 'COMPLETED':
                raise JobError('OCR_JOB_NOT_READY')
            result = self._result(ident)
            result['artifact']['capability'] = capability
            return result

    @safe_storage
    def open(self, ident, capability):
        with self.lock:
            result = self.result(ident, capability)
            fd = os.open(self._path(ident, 'output.pdf'), os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
            handle = os.fdopen(fd, 'rb')
            info = os.fstat(handle.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size != result['artifact']['sizeBytes']:
                handle.close()
                raise JobError('OCR_JOB_CORRUPT')
            self.active[ident] = self.active.get(ident, 0) + 1
            return ArtifactLease(self, ident, handle, result['artifact']['sizeBytes'])

    @safe_storage
    def cancel(self, ident, capability):
        with self.lock:
            self.sweep()
            record = self._auth(ident, capability)
            if record['state'] != 'COMPLETED':
                record.update(state='CANCELLED', phase='CANCELLED', updatedAt=time.time())
                self._save(record)
            return self._status(record)

    @safe_storage
    def acknowledge(self, ident, capability):
        with self.lock:
            record = self._auth(ident, capability)
            if record['state'] == 'ACKNOWLEDGED':
                return
            if record['state'] != 'COMPLETED':
                raise JobError('OCR_JOB_NOT_READY')
            if ident in self.processing or self.active.get(ident):
                raise JobError('OCR_JOB_BUSY')
            # Durable acknowledgement precedes deletion. A failed write retains the result.
            updated = dict(record, state='ACKNOWLEDGED', phase='ACKNOWLEDGED', updatedAt=time.time())
            self._save(updated)
            self.records[ident] = updated
            try:
                self.sweep()
            except JobError:
                # Already acknowledged: keep its private tombstone/quota for cleanup retry.
                pass

    @safe_storage
    def fail(self, ident, code):
        with self.lock:
            record = self.records[ident]
            if record['state'] != 'CANCELLED':
                record.update(state='ERROR', phase='ERROR', errorCode=code, updatedAt=time.time())
                self._save(record)

    @safe_storage
    def requeue(self, ident):
        with self.lock:
            record = self.records[ident]
            if record['state'] == 'RUNNING':
                record.update(state='QUEUED', phase='QUEUED')
                self._save(record)

    @safe_storage
    def sweep(self, now=None):
        with self.lock:
            now = time.time() if now is None else now
            for ident, record in list(self.records.items()):
                if record['state'] in {'QUEUED', 'RUNNING'} or ident in self.processing or self.active.get(ident) or record['state'] != 'ACKNOWLEDGED' and now - record['updatedAt'] < self.ttl:
                    continue
                folder = self.root / ident
                files = list(folder.iterdir())
                for path in files:
                    if path.is_symlink() or not path.is_file():
                        raise JobError('OCR_JOB_CORRUPT')
                # Keep the bounded ownership manifest last for restart-safe partial cleanup.
                for path in sorted(files, key=lambda p: p.name == 'job.json'):
                    path.unlink()
                folder.rmdir()
                del self.records[ident]
