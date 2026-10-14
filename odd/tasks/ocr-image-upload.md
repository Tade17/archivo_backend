# OCR and ordered image uploads

## Objective
Restore PDF OCR, combine images in upload order with automatic OCR, and simplify navigation while preserving later attachments and original files.

## Authorized scope and constraints
- Local backend/frontend changes and rebuilding only the local OCR service.
- Keep uploaded PDFs separate; combine images in received order, not filename order.
- Preserve original image bytes and their association, authenticated access, and cleanup.
- Keep existing limits, transaction rollback, and Add documents from expediente details.
- No push, pull request, merge, database reset, or destructive Docker operations.
- Existing Spanish UI and comments may retain professional Spanish.

## Delivery
- Feature branch: fix/ocr-image-upload in both repositories; main remains unchanged.
- delivery_strategy: ask-on-risk; chain_strategy: feature-branch-chain (user selected).
- Forecast: 580–940 authored changed lines, excluding generated files.
- Coherent work-unit commits with tests/docs; no remote chain created.

## Tasks
- [x] T1: Fix OCR error mapping, add rendering regression, rebuild OCR and verify synthetic PDF. Route: delegated; preparation, multi-file code and execution triggers. Commit `91ab901`; parent reran 19 passing tests; native assessment medium/under_budget (278 lines), no review due yet.
- [ ] T2: Group ordered images into one PDF, preserve originals, enforce bounds and rollback, test/download/delete behavior. Route: delegated; multi-file implementation trigger.
- [ ] T3: Remove global batch-navigation entry, retain Add documents route, clarify grouping and order in both upload flows, test/build. Route: delegated; multi-file UI trigger.

## Acceptance and verification
- T1: Python rendering regression; isolated OcrClient tests; local health and synthetic processing without user data.
- T2: Tests for image order, interleaved PDFs, TIFF frames, malformed files, limits, original association and rollback.
- T3: Navigation and upload UI tests, production build, existing visual identity preserved.
- Use observed RED/GREEN for runnable deterministic tests; disclose unavailable checks.
- RDD is on globally in both repos; candidate consent remains required when native provider requests it.
- Track native review outcomes and commit identities per work unit; no fabricated approval.

## Progress
- Planning complete; repositories clean before branch creation.
- Engram mirror pending: provider cannot resolve multi-repo workspace; user asked not to save now. Local document remains authoritative until reconciled.
- Current reviewed boundary: initial branch point/main; T1 remains in the pending under-budget slice.
- T1 verification: `./mvnw.cmd -Dtest=OcrClientTest test` observed RED (10 tests, 8 assertion failures), then GREEN (19 tests, no failures/errors/skips). In-process mock server avoids the Windows JDK loopback-selector failure seen with the first test harness.
- T1 rendering: new Python unittest regression ran against original Docker image via stdin, RED (3 context-manager TypeErrors). After `docker compose up -d --build ocr`, same suite GREEN (3 tests). Current committed `app.py` already had explicit close; no further source correction was needed there.
- T1 runtime: rebuilt OCR image `sha256:5ca6e8e03798413f57899ae81cd1d96f2fa6d65f3aad585d15e99c9b1a4dce83`, container healthy, explicit document close confirmed. Synthetic one-page PDF returned `ARCHIVO OCR TEST`, a 37,000-byte searchable PDF with extractable text, and `/pdf` regenerated the PDF successfully. PostgreSQL was not recreated; no storage was deleted.
- T1 checks: `git diff --check` passed; production integration/DB suite intentionally not run. Commit `91ab901`. Rollback boundary: OcrClient error mapping, its focused Java test, rendering Python test, OCR troubleshooting documentation, and OCR runtime image replacement; original documents remain untouched.
- T2 implemented: signature-based grouping at first-image position, ordered TIFF frames, originals stored byte-for-byte with a bounded internal JSON manifest, authenticated original metadata/download, rollback cleanup and after-commit deletion. Converter uses existing Pillow/ReportLab modules without adding dependencies. Parent authorized optional bounded PDF sink in `ocr_service/searchable_pdf.py` and migration path correction to `V12__documento_originales.sql` because tracked V9/V10/V11 already exist.
- T2 Java: observed RED (4 batch assertions), then GREEN: `./mvnw.cmd -Dtest=OcrClientTest,DocumentoImageBatchServiceTest,DocumentoDigitalControllerTest test` and fresh `clean test`, 46 passing tests (25 client, 16 batch/storage/deletion, 5 controller/auth/multipart), no failures/errors/skips. No database integration suite run.
- T2 Python: observed RED (11 batch tests, missing implementation), then 14 passing rendering/batch tests inside actual rebuilt OCR runtime. `test_searchable_pdf` could not import missing development dependency `pypdf`; no package installed. Local Python also lacks reportlab. Existing suite's PDFium context-manager use corrected; full historical Unicode suite remains pending dependency availability.
- T2 runtime: only OCR rebuilt, healthy image `sha256:f036f32d2e4dfa2bfbd7981590f63558c6946a43a56dbffefda0d620fdd785d2`. Synthetic TIFF(two frames)+PNG conversion returned 3 ordered pages (red/green/blue), 15,109-byte PDF; subsequent OCR recognized FIRST/SECOND/THIRD PAGE in order. Multipart binding separately verified with MockMvc. PostgreSQL not recreated; no user documents touched.
- T2 deployment pending: backend Java process still runs old code. Parent must verify restart target and database safety before starting updated backend; Flyway V12 only adds nullable TEXT manifest column, no backfill. Migration has not been applied manually or against live DB. Full-stack readiness is not yet claimed.
- T2 migration correction evidence: complete numeric inventory verified unique V1 through V12; existing tracked V9, V10 and V11 untouched. Initial tail-only inspection missed lexically earlier versions, corrected after full inventory. Parent authorized renaming only the new untracked migration to V12. `./mvnw.cmd -q clean` removed stale generated migration resources after rename; next verification/deployment must rebuild. No startup or live Flyway execution was tested.
- T2 checks: `git diff --check` passed before final handoff; native review and work-unit commit pending. Proposed coherent slices: converter/client plus tests (~294 authored lines), then transactional upload/original-manifest integration plus tests/docs (~713). Integration is larger than the advisory budget because storage rollback, association and protected downloads must work together; no tests omitted to reduce size.
- Next: parent independent verification/native review/commit T2; then T3 and controlled backend deployment.
