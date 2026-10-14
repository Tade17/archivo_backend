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
- [x] S1: Small JSON metadata and bounded binary PDF artifacts, atomic storage and safe diagnostic codes; compatible legacy endpoints. Delegated: multi-file/preparation/execution triggers.
- [x] S2: Original-PDF invisible text overlays, bounded page rendering, Unicode/rotation/cropbox regressions; pin verified BSD pypdf dependency. Delegated: multi-file/PDF verification triggers. Implementation/checks complete; commit and deployment pending.
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
- S1 committed as 5321588 and independently verified. S2 implemented and checked; parent verification, work-unit commit and activation pending. Neither candidate has been deployed live. Next: parent checks the frozen S2 candidate before committing.
- Operational prior recovery and backup: see OCR.md and odd/tasks/ocr-image-upload.md.
- S3-S6 remain unimplemented. Implementation checkboxes do not authorize commit, review or deployment.

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
- S1 checks: source byte normalization before final verification; `git diff --check` passed before doc updates. No new S1 dependency. Historical `test_searchable_pdf` was unavailable during S1 (`pypdf` absent), resolved in S2 below. No S1 10/40-page benchmark, durable-job test or throughput improvement claim.
- Best coherent units for review: artifact cache/v2 Python contract with cache quotas and tests; Java metadata/download/storage/publication boundary with constrained-reader and cleanup tests. They depend on shared safety contracts and may exceed the advisory 400-line heuristic; do not split tests away or compress them.
- Authored S1 change count at handoff: 1071 additions plus deletions excluding this task document; 1130 including its 59 lines. Coherent technical groups are approximately 514 Python/cache/Compose lines and 527 Java/storage/test lines, plus operating documentation. No generated files counted.
- S1 commit: 5321588. Parent reran 41 Java tests GREEN; independent verifier found no deterministic blocker. Native assessment: medium, 2473 changed lines, review_due=false, under_budget (provider outcome authoritative); review boundary main remains pending. No terminal approval claimed. Local task authoritative, mirror pending provider ambiguity/no-save request. Running OCR/backend remain on prior deployed transport until parent-controlled activation.

## S2 evidence and recovery
- Route: delegated direct (multi-file/PDF verification triggers). Implementation/checks complete; commit, parent checks/native assessment and live activation pending. S3-S6 not started.
- Behavior: invisible Unicode ReportLab text-only overlay through pypdf retains original compressed image streams, page order, media/crop boxes and source rotations. Original bytes/hash are never overwritten. Legacy PDF regeneration uses the same overlay; legacy image endpoints remain compatible.
- Ownership: PDF/TIFF header preflight rejects more than 100 pages or 250 million aggregate pixels before rendering. Recognition consumes a generator, closing current RGB raster, PDFium bitmap/page/document and TIFF frame on advance, cancellation or inference error. A compatibility list wrapper remains, but recognition never calls it. Image recognition writes one page at a time. Writer/source structures, encoded pages, metadata and models remain resident; not constant total memory.
- Dependency: pinned pypdf==6.20.1, BSD-3-Clause, Python >=3.9 including Python 3.12. Sources: [official release](https://pypi.org/project/pypdf/6.20.1/) and [merge transformations](https://pypdf.readthedocs.io/en/stable/user/add-watermark.html). Installed only in isolated candidate image, not globally/production.
- RED: iterator regressions observed four missing-iterator errors (seven tests). Overlay/rendering regressions then observed eight missing-implementation errors (eleven tests) before implementation.
- Historical comparator failure: initial full suite failed test_invisible_layer_preserves_visible_scan at bbox (100,202,231,212). Same failure reproduced against committed S1 searchable_pdf.py with pinned pypdf: direct source-raster/PDFium comparison included fractional page sizing/resampling. Corrected test asserts decoded XObject RGB equals source bytes, then compares identically rendered text-free/text-overlay PDFs exactly; no pixel tolerance introduced.
- Final GREEN: 46 tests, zero failures/errors/skips, 2.137 seconds: overlay 4, rendering 8, historical searchable PDF 5, OCR contract 7, image batch 11, artifact store 11. Tests import packaged /app code; test files readonly-mounted. UTF-8/LF normalization preceded final verification.
- Exact final suite command from backend (PowerShell):

```powershell
docker run --rm --network none --mount "type=bind,source=$PWD/ocr_service,target=/tests,readonly" --tmpfs /artifacts:rw,size=536870912 -e OCR_ARTIFACT_DIR=/artifacts -e PYTHONDONTWRITEBYTECODE=1 -e PYTHONPATH=/tests -w /app archivo-ocr-s2-candidate:local python -m unittest test_pdf_overlay test_app_rendering test_searchable_pdf test_ocr_contract test_image_batch test_artifact_store
```

- Candidate image: archivo-ocr-s2-candidate:local, sha256:04a1a73eb111078d4555d9d289c0cd1d864457d8d47eea32a832e3f04d5b5e23. No running service changed.
- PDF regressions: exact rendered RGB equality at 220 DPI for mixed sizes, crop offsets and 0/90/180/270-degree rotations; compressed image data/filters unchanged; Unicode search and independently checked first-glyph device position; nonzero media origin, order, bounds and failure cleanup. Test forbids background redraw.
- Actual-model synthetic ten-page smoke completed in one isolated no-network container: 232466 input bytes, 258089 output bytes (1.11x), recognition 5.129 seconds, process maxRSS 538524 KiB. Original hash/compressed page-image streams unchanged; exact visible pixels at 72 DPI across all ten pages; searchable PAGE labels in received order. Single smoke is not a throughput benchmark, speedup claim or whole-container/VM memory guarantee.
- Failed/skipped/pending: historical comparator initially failed as documented, now passes; no user-document upload, live endpoint/database test, Java rerun for Python-only S2, authenticated browser check, 40-page benchmark or durable-job recovery. Isolated proof does not imply these checks.
- Rollback: only S2 app/searchable PDF/dependency and four test files (including new test_pdf_overlay.py), plus task/OCR documentation; retain S1 transport/artifact storage. Coherent unit couples raster ownership, geometry and image compatibility with regressions, exceeding advisory 400 lines without omitting tests or compressing source.
- Authored S2 changes: 591 functional/test lines, 636 including documentation (additions plus deletions, generated files excluded). Final structural readback and git diff --check passed. Local mirror pending provider ambiguity/no-save request.
