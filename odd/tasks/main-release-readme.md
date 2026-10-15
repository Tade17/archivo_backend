# Collaborator onboarding and main publication

## Objective and authorization
Publish the verified OCR/upload changes to main in both repositories and make first installation and safe updates understandable for collaborators.

- User explicitly authorized main publication in Tade17/archivo_backend and Tade17/archivo_frontend using the Git authentication configured on this PC.
- Authorized local documentation edits, checks, normal commits/merges and non-force pushes to those exact origin/main destinations only.
- No force push, history rewrite, credential/account changes, unrelated remote operations, destructive database reset or volume deletion.
- Existing Spanish documentation remains neutral/professional Spanish. Do not publish supplied test credentials, JWTs, actual environment secrets, database dumps or document storage.

## Delivery and initial state
- Both worktrees clean on fix/ocr-image-upload before this document.
- Fresh origin fetch: backend local main equals origin/main at aa6bece; frontend equals origin/main at 825dc8e.
- User now explicitly requests direct main delivery; this overrides the earlier proposed feature-branch-chain publication shape. Preserve coherent existing commits; no PR requested.
- Forecast: approximately 500–900 authored documentation lines; no code compression or artificial splitting. Main integration must be fast-forward when possible; stop on divergence/protection instead of forcing.
- Existing functional checks: backend 94 focused tests, Python 79 tests and frontend 48 tests/build observed previously; current release verification must record its own actual results.
- Native review outcomes remain provider-owned. Prior under-budget assessments are not review approval.
- Engram mirror pending because the multi-repository provider context is unresolved; this document is the local recovery source.

## Tasks
- [ ] R1: Refresh both README files and narrowly correct linked operational guidance so installation and update steps match current configuration. Route: delegated direct; multi-file preparation/write trigger. Commit documentation with structural readback.
- [ ] R2: Verify documented commands/configuration, complete isolated V1–V13 migration/validation if safely runnable without launching a denied server, run proportional release checks and scan the outgoing changes for unintended secrets/artifacts. Route: delegated verification; execution and independent evidence trigger.
- [ ] R3: Recheck origin/main state, fast-forward local main without rewriting history, push normally to both authorized origins, and verify remote/local commit identity. Route: parent Git state/publication; no force or destructive recovery.

## Acceptance
- Separate requirements and quick start for Docker dependencies, Java backend and Angular frontend, with real ports/versions/readiness expectations.
- Explain first administrator setup without copying credentials, Compose versus Java environment configuration, persistent files/job volumes, safe backups/updates, and development versus production API routing.
- Do not advertise a generic full Maven suite as safe against a shared database. List actual isolated/quick checks and truthful installation-verification limits.
- No personal absolute paths in portable onboarding commands, invented configuration variables, stale v1 OCR-only checks or generic database repair/reset advice.
- No repository source/config/schema changes unless a necessary new scope is explicitly reviewed; preserve live services/data while testing in disposable labelled fixtures only.
- Record failed/skipped/unavailable checks honestly. A runtime policy denial cannot be bypassed with another launcher.

## Progress
- R1 documentation draft complete in both README files and the two linked backend operational guides; neutral Spanish, portable PowerShell-first commands and no copied credentials or personal paths.
- R1 observed structural checks: all relative Markdown file links resolve; targeted secret/personal-path patterns found zero matches in the four changed guides; both repository diff checks pass; Docker Compose configuration validates without launching services.
- R1 preserves runtime/configuration/schema files. No services launched, database accessed, migrations applied, backups created, commits made or remote publication performed by the writer.
- Documentation readback confirmed Compose-only dependencies, Java17, locked Angular/Node/npm requirements, V1–V13, v2 job readiness, binary-safe backup instructions, separate document/job persistence and production API proxy requirements.
- R1 structural review is ready; R2 independent verification passed. Checkboxes and commit identities remain pending the parent commit; main publication R3 is next.
- R2 observed checks: 94 focused Java tests, 79 Python tests, 48 frontend tests and the frontend build passed; Compose configuration and relative documentation links passed.
- R2 used the direct Flyway Java API against an isolated PostgreSQL17 fixture: V1–V13 migrated and validated, 13 successful history entries, one ADMIN seed and 11 nullable V13 columns verified. No HTTP server, seed login or full OCR/browser round trip was exercised.
- The isolated fixture guard initially refused before schema writes because it used obj_description for a shared database comment. The operational harness was corrected to shobj_description; the exact-ID/label-owned fixture was removed afterward. Production data and services were untouched.
- R2 outgoing pattern scan covered 224 backend and 89 frontend tracked files: no new private key, token, JWT or supplied test secret and no new data artifacts found. Existing credential-like assignments in scripts/seed-demo are unchanged; this is not an exhaustive historical security audit.
- Verification reports are in output/main-release-verification outside both repositories. Two OCR documentation statements were corrected after review: durable artifacts can reuse job capabilities, and original metadata listing requires authentication but is not an audited file download.
