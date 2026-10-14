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
- Actual functional slice: approximately 1292 backend lines and 482 frontend lines. The forecast was exceeded by coupled transactional storage/original-access safety and regression coverage; no artificial compression or omission of tests.

## Tasks
- [x] T1: Fix OCR error mapping, add rendering regression, rebuild OCR and verify synthetic PDF. Route: delegated; preparation, multi-file code and execution triggers. Commit `91ab901`; parent reran 19 passing tests; native assessment medium/under_budget (278 lines), no review due yet.
- [x] T2: Group ordered images into one PDF, preserve originals, enforce bounds and rollback, test/download/delete behavior. Route: delegated; multi-file implementation trigger. Backend commit `e0225b9`; independent 46-test verification passed.
- [x] T3: Remove global batch-navigation entry, retain Add documents route, clarify grouping and order in both upload flows, test/build. Route: delegated; multi-file UI trigger. Frontend commit `9167eb9`; parent reran 15 passing tests; production build passed.

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
- Current reviewed boundary: main in both repositories. Native provider assessed backend main..HEAD medium/1292 lines and frontend main..HEAD medium/482 lines, both `review_due=false`, reason `under_budget`. The slices remain pending; no candidate consent, native approval or acknowledgement is fabricated. Keep these provider outcomes as returned.
- T1 verification: `./mvnw.cmd -Dtest=OcrClientTest test` observed RED (10 tests, 8 assertion failures), then GREEN (19 tests, no failures/errors/skips). In-process mock server avoids the Windows JDK loopback-selector failure seen with the first test harness.
- T1 rendering: new Python unittest regression ran against original Docker image via stdin, RED (3 context-manager TypeErrors). After `docker compose up -d --build ocr`, same suite GREEN (3 tests). Current committed `app.py` already had explicit close; no further source correction was needed there.
- T1 runtime: rebuilt OCR image `sha256:5ca6e8e03798413f57899ae81cd1d96f2fa6d65f3aad585d15e99c9b1a4dce83`, container healthy, explicit document close confirmed. Synthetic one-page PDF returned `ARCHIVO OCR TEST`, a 37,000-byte searchable PDF with extractable text, and `/pdf` regenerated the PDF successfully. PostgreSQL was not recreated; no storage was deleted.
- T1 checks: `git diff --check` passed; production integration/DB suite intentionally not run. Commit `91ab901`. Rollback boundary: OcrClient error mapping, its focused Java test, rendering Python test, OCR troubleshooting documentation, and OCR runtime image replacement; original documents remain untouched.
- T2 implemented: signature-based grouping at first-image position, ordered TIFF frames, originals stored byte-for-byte with a bounded internal JSON manifest, authenticated original metadata/download, rollback cleanup and after-commit deletion. Converter uses existing Pillow/ReportLab modules without adding dependencies. Parent authorized optional bounded PDF sink in `ocr_service/searchable_pdf.py` and migration path correction to `V12__documento_originales.sql` because tracked V9/V10/V11 already exist.
- T2 Java: observed RED (4 batch assertions), then GREEN: `./mvnw.cmd -Dtest=OcrClientTest,DocumentoImageBatchServiceTest,DocumentoDigitalControllerTest test` and fresh `clean test`, 46 passing tests (25 client, 16 batch/storage/deletion, 5 controller/auth/multipart), no failures/errors/skips. No database integration suite run.
- T2 Python: observed RED (11 batch tests, missing implementation), then 14 passing rendering/batch tests inside actual rebuilt OCR runtime. `test_searchable_pdf` could not import missing development dependency `pypdf`; no package installed. Local Python also lacks reportlab. Existing suite's PDFium context-manager use corrected; full historical Unicode suite remains pending dependency availability.
- T2 runtime: only OCR rebuilt, healthy image `sha256:f036f32d2e4dfa2bfbd7981590f63558c6946a43a56dbffefda0d620fdd785d2`. Synthetic TIFF(two frames)+PNG conversion returned 3 ordered pages (red/green/blue), 15,109-byte PDF; subsequent OCR recognized FIRST/SECOND/THIRD PAGE in order. Multipart binding separately verified with MockMvc. PostgreSQL not recreated; no user documents touched.
- T2 deployment completed through controlled parent restart: Java 24 backend PID 15860, Tomcat 8080; Flyway V12 applied and nullable TEXT manifest column validated. The migration adds no backfill. No manual migration or database reset. Runtime smoke checks do not establish authenticated end-to-end upload success.
- T2 migration correction evidence: complete numeric inventory verified unique V1 through V12; existing tracked V9, V10 and V11 untouched. Initial tail-only inspection missed lexically earlier versions, corrected after full inventory. Parent authorized renaming only the new untracked migration to V12. `./mvnw.cmd -q clean` removed stale generated migration resources; independent verification rebuilt before deployment.
- T2 checks: `git diff --check` passed; backend work-unit commit `e0225b9`. Best coherent conceptual units remain converter/client plus tests (~294 authored lines) and transactional upload/original-manifest integration plus tests/docs (~713). The implemented safety unit exceeds the advisory budget because rollback, association and protected downloads must work together; do not split by file type or compress tests.
- Next: optional authenticated desktop/mobile visual and synthetic end-to-end upload validation, then user-owned publishing decision. No push, PR or merge performed.

## Recovery across repositories
- Backend: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/archivo_backend`, branch `fix/ocr-image-upload`, T1 `91ab901`, T2 `e0225b9`.
- Frontend: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/archivo_frontend`, branch `fix/ocr-image-upload`, T3 `9167eb9`.
- Frontend task: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/archivo_frontend/odd/tasks/ocr-image-upload.md`.
- Both local task documents are authoritative; Engram mirror remains pending provider ambiguity and the user's explicit no-save request.
- Original images are preserved with hashes/ordering and accessible via authenticated list/download APIs; a new original-image download UI was not built. Image/PDF limits remain 100 files, 20 MB per file, 20 MB total images and combined PDF, 100 pages/250M pixels; UI additionally retains 150 MB overall selection cap.

## Deployment recovery evidence (2026-10-14)
- Initial Java 24 and installed Java 21 startup attempts failed `Unable to establish loopback connection` / `Invalid argument: connect`. Switching selector providers was also disproven. No application-source fix or persistent environment/security change was made.
- Parent verified the JDK `UnixDomainSocketsUtil` property and `PipeImpl` TCP fallback using standalone `Selector.open()` before recovery. Successful restart used a session-only nonexistent Unix-domain socket directory; preserve its nonexistence.
- From the backend directory, use the proven operational command:

```powershell
.\mvnw.cmd "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/selector-probe-do-not-create" spring-boot:run
```

- Do not create `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/selector-probe-do-not-create` or persist this property globally.
- Pre-migration backup: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/archivo_sanjose-pre-v12-20261014-120932.dump` (PGDMP, 112 entries, 55,461 bytes, validated before migration).
- Successful launch log: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/backend-recovery-tcp.stdout.log`.
- Failed Java 24 log: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/backend-20261014-120932.stdout.log`.
- Failed Java 21 log: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/backend-recovery-java21.stdout.log`.
- Restore is not authorized. Preserve backup/logs and the additive column; dropping populated manifests would lose original associations.

## Verification checklist
- [x] Independent backend tests: 46 passing; parent frontend tests: 15 passing; production frontend build passed.
- [x] Actual OCR runtime: 14 Python rendering/batch tests and ordered synthetic TIFF+PNG conversion followed by OCR passed.
- [x] Numeric migration inventory unique V1–V12; runtime Flyway V12 and nullable TEXT column validated.
- [x] Backend `/v3/api-docs` HTTP 200 includes original endpoints; unauthenticated original request HTTP 401; frontend 4200 HTTP 200; OCR healthy.
- [x] Existing state snapshot: 4 completed documents, 2 existing errors, 0 pending; no user records created or user PDFs modified.
- [ ] Historical `test_searchable_pdf`: unavailable because `pypdf` is absent; no dependency installed.
- [ ] Authenticated desktop/mobile visual inspection: parent reached login only; not performed.
- [ ] Live authenticated end-to-end upload: not performed; only synthetic OCR service and mocked multipart checks completed.
- [ ] Native review receipt: none; provider reported `under_budget`, not approved.
