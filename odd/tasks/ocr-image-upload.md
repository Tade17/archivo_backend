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
- [x] T1: Fix OCR error mapping, add rendering regression, rebuild OCR and verify synthetic PDF. Route: delegated; preparation, multi-file code and execution triggers. Implementation/checks complete; work-unit commit and native review pending.
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
- Current boundary: initial branch point; commit identities pending.
- T1 verification: `./mvnw.cmd -Dtest=OcrClientTest test` observed RED (10 tests, 8 assertion failures), then GREEN (19 tests, no failures/errors/skips). In-process mock server avoids the Windows JDK loopback-selector failure seen with the first test harness.
- T1 rendering: new Python unittest regression ran against original Docker image via stdin, RED (3 context-manager TypeErrors). After `docker compose up -d --build ocr`, same suite GREEN (3 tests). Current committed `app.py` already had explicit close; no further source correction was needed there.
- T1 runtime: rebuilt OCR image `sha256:5ca6e8e03798413f57899ae81cd1d96f2fa6d65f3aad585d15e99c9b1a4dce83`, container healthy, explicit document close confirmed. Synthetic one-page PDF returned `ARCHIVO OCR TEST`, a 37,000-byte searchable PDF with extractable text, and `/pdf` regenerated the PDF successfully. PostgreSQL was not recreated; no storage was deleted.
- T1 checks: `git diff --check` passed; production integration/DB suite intentionally not run. Commit pending parent review. Rollback boundary: OcrClient error mapping, its focused Java test, rendering Python test, OCR troubleshooting documentation, and OCR runtime image replacement; original documents remain untouched.
- Next: parent review/commit T1, then T2.
