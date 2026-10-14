# Reliable and recoverable multipage OCR

## Objective and problem
Deliver large searchable PDFs without Base64 response expansion, process long documents with recoverable page checkpoints, and measure throughput before tuning. The latest OCR HTTP 200 was followed by invalid-response parsing; an oversized Jackson string was reproduced, but the original exception was not retained.

## Authorization and constraints
- User authorized implementing the recommended reliability, recovery, and measured optimization plan.
- Local backend/frontend/OCR changes only; preserve original bytes and useful existing PDFs.
- No push, PR, merge, destructive database reset, volume deletion, paid GPU, or remote service.
- Use synthetic documents and isolated databases for testing; do not silently retry historical ERROR documents.
- Keep security limits: bounded binary artifacts, capability-protected access, private storage paths, bounded admission and safe diagnostics.
- Existing professional Spanish UI preserved; code/new recovery artifacts default English.
- Existing Java 24 runtime needs the documented session-only Unix-domain directory workaround; do not persist system changes.

## Delivery and recovery
- Branch: fix/ocr-image-upload in both repositories; main untouched.
- delivery_strategy: ask-on-risk; chain_strategy: feature-branch-chain (already chosen by user).
- Forecast: 2320–3770 authored lines, generated files excluded. Coherent units may exceed advisory 400-line slices; preserve tests and safety.
- One writer at a time; read-only verification may be independent.
- Initial review boundaries remain main; native assessment owns review_due and candidate consent.
- Engram mirror pending: provider cannot resolve this multi-repository workspace; no session identity invented.

## Tasks
- [ ] S1: Small JSON metadata and bounded binary PDF artifacts, atomic storage and safe diagnostic codes; compatible legacy endpoints. Delegated: multi-file/preparation/execution triggers.
- [ ] S2: Original-PDF invisible text overlays, bounded page rendering, Unicode/rotation/cropbox regressions; pin verified BSD pypdf dependency. Delegated: multi-file/PDF verification triggers.
- [ ] S3: Durable Python jobs with per-page checkpoints, bounded admission, deduplication and resumable artifact downloads. Delegated: multi-file implementation triggers.
- [ ] S4: Continuously drained PostgreSQL jobs with leases, bounded retries and page progress; additive migration V13 after complete inventory check. Delegated: multi-file/isolated integration triggers.
- [ ] S5: Existing document view shows actual N/M page and phase progress without new routes; polling/retry cleanup tests. Delegated: multi-file UI triggers.
- [ ] S6: Synthetic baseline/candidate benchmark, resource measurements and conservative verified CPU settings; no unmeasured speedup claim. Delegated: execution and model-runtime verification triggers.

## Acceptance and checks
- Runnable deterministic regressions observe RED/GREEN; no artificial 100 MB allocations when constrained-reader tests suffice.
- S1: binary content/signature/size/truncation checks, no capability/path disclosure, quota/TTL tests, partial-file cleanup and retry without inference.
- S2: original bytes/page streams preserved; searchable Unicode, page order, dimensions, rotations/cropboxes and visual rendering comparisons.
- S3/S4: restart mid-document resumes completed pages; duplicate submission/stale lease cannot publish; >100 queued jobs safely drain; active jobs never removed by TTL.
- S5: backward-compatible null progress, accessible actual status, tests and production build.
- S6: approved synthetic 1/10/40-page scans and image batch, sequential isolated benchmark; timing/memory/size/recognition evidence determines defaults.
- Live database backed up before additive migration; preserve OCR artifact volume; verify service readiness separately from schema success.
- Required failed/skipped/unavailable checks recorded honestly; authenticated user-document upload not assumed.

## Progress
- S1 implemented and self-verified; independent verification/native assessment/work-unit commit pending. No live deployment. Next: parent verifies S1 before S2.
- Operational prior recovery and backup: see OCR.md and odd/tasks/ocr-image-upload.md.
- Tests, commit identities, actual line counts and native review outcomes pending.

## S1 evidence and recovery
- Route: delegated direct (multi-file/preparation/execution triggers). Exact additional authorized surface: `ocr_service/Dockerfile`, solely copying `artifact_store.py` before app/model initialization.
- Contract: Java calls `/v2/ocr` for at most 16 MiB JSON metadata with no PDF Base64, then capability-protected binary download by strict opaque ID. No arbitrary artifact URL or HTTP redirect accepted. Legacy Python `/ocr`, `/pdf`, `/images/pdf` retained.
- Cache: single-worker persistent `archivo_ocr_artifacts` named volume; independent random ID/capability, hashed capability manifest, constant-time comparison, no symlink following, atomic publication, active-download protection. Defaults/hard upper bounds: 128 MiB/artifact, 512 MiB PDF cache including interrupted partial writes, 32 artifacts/reservations, one-hour TTL; private manifests bounded to 4 KiB each. No existing volume removed.
- Storage: capped streaming, mandatory Content-Length/type, signature, actual size and SHA-256 checks; atomic publication and partial cleanup. Previous useful PDF removed only after successful database update. One bounded I/O download retry uses the same retained artifact, not inference. This does not yet provide durable job/page recovery across backend restarts (S3/S4 pending).
- Diagnostics: static safe failure codes, request correlation, input/artifact/metadata sizes, duration and exception class; no original names/paths, text, capabilities or traceback payloads. No globally increased Jackson limits.
- RED: Python artifact-store import absent before implementation; contract tests observed 3 missing-v2 errors. Java stream storage observed 4 failed assertions/1 error; 4 worker tests failed against the legacy Base64 publication path. First constrained-reader test attempt used the wrong Jackson 3 JsonFactory package; installed jar verified `tools.jackson.core.json.JsonFactory`, corrected and fresh compilation performed. Do not treat that compilation failure as behavioral RED.
- GREEN: exact `./mvnw.cmd -Dtest=OcrClientTest,OcrWorkerTest,FileSystemStorageServiceTest test` and fresh `clean test` passed 41 tests (32 client, 4 worker, 5 storage), zero failures/errors/skips. In-process MockRestServiceServer avoids the known Windows selector issue. Constrained-reader regression rejects a 512-character legacy Base64 value at maxStringLength128 while new small metadata parses; no artificial 100 MB allocation.
- GREEN Python: isolated cached Docker image, no network, readonly source mount, temporary cache and PYTHONDONTWRITEBYTECODE=1; `python -m unittest test_artifact_store test_ocr_contract test_app_rendering test_image_batch` passed 31 tests (11 store, 6 contract, 14 prior regressions). Covers TTL/count/bytes/partial quota, recreation, constant-time primitive, traversal/symlinks, active download/disconnect, cleanup, private logs and legacy compatibility.
- Real isolated one-page OCR smoke: 574-byte metadata, 35,792-byte PDF, recognized/searchable BINARY OCR TEST, two byte-identical protected downloads with verified SHA-256, zero active leases afterward. Only synthetic data; no live endpoint, database, user record or historical retry.
- Checks: source byte normalization before final verification; `git diff --check` passed before doc updates. No new dependency. Historical `test_searchable_pdf` remains unavailable (`pypdf` absent); its dependency is planned for S2. No 10/40-page benchmark, durable-job test or throughput improvement claim yet.
- Best coherent units for review: artifact cache/v2 Python contract with cache quotas and tests; Java metadata/download/storage/publication boundary with constrained-reader and cleanup tests. They depend on shared safety contracts and may exceed the advisory 400-line heuristic; do not split tests away or compress them.
- Authored S1 change count at handoff: 1071 additions plus deletions excluding this task document; 1130 including its 59 lines. Coherent technical groups are approximately 514 Python/cache/Compose lines and 527 Java/storage/test lines, plus operating documentation. No generated files counted.
- Commit/native receipt pending parent; review boundary remains main. Local task authoritative, mirror pending provider ambiguity/no-save request. Running OCR/backend remain on the prior deployed transport until parent-controlled activation.
