# Changes and explicit boundaries

## Source baseline

Current uploads: patient-queue-backend-copy.zip, patient-queue-flutter-copy.zip, feedback_backend-copy(2).zip, feedback-frontend-copy(2).zip. Original archives and extraction copies were not modified. Older v3/v4 reporting code was recovered for reference, not blindly trusted or installed as a prerequisite.

## PQueue 5.0

- Consolidated unapplied advanced reporting into date/doctor filters, summary, daily/hourly distribution, workload, previous-period comparison, quality checks and aggregate CSV.
- Fixed a recovered SQL fragment boundary that could concatenate the named doctor parameter with GROUP BY.
- Moved combined dashboard reads into a repeatable-read read-only transaction and made Flutter load one snapshot.
- Added explicit empty-denominator behavior and valid wait/duration cohorts; CSV uses the same eligibility rules.
- Added missing-reference/check-in and status/timestamp quality flags without changing records.
- Injected clinic Clock for queue dates and transition times; server provides clinicToday to the UI.
- Serialized visit state changes with a single-row FOR UPDATE lock; kept existing atomic token allocator and database constraints.
- Kept registration-dialog controller ownership fix; added save/cancel regression test.
- Added API timeout messages, authenticated401 handling, in-memory token/session clearing, queue refresh and stale-response protection.
- Changed visible branding to PQueue; retained package/module identities to avoid a rename migration.
- Added PowerShell5.1 launch/test/read-only smoke scripts with stable ports and DPAPI key persistence.
- Runtime application.properties, schema/data, pom dependency versions and Flutter lock versions remain unchanged by the installer.

## VoxFlow 2.0

- Replaced the small legacy frontend/backend with a structured creator/public workflow.
- Persisted rooms before submission; introduced explicit creator ownership and authentication.
- Added salted scrypt passwords, hashed opaque sessions, cookie/Origin/CSRF controls and rate limits.
- Replaced plain response strings with bounded embedded structured records for atomic submission/closure behavior on standalone MongoDB.
- Added room open/close/archive, private inbox, category/status changes, filters, UTC charts, pagination and CSV formula escaping.
- Retired unsafe legacy read endpoints; explicit preview-first operator import keeps original data and unknown timestamps intact.
- Rebuilt responsive UI with user-visible loading, errors, empty states and privacy boundaries.
- Preserved uploaded npm dependency versions and lock resolutions; only root metadata/scripts changed.

## Not included / not claimed

- Clinical documents, voice recording/transcription, patient-specific clinical authorization or healthcare compliance.
- AI sentiment, RAG, LLM integrations, automated business recommendations or claimed measurable impact.
- Dockerization, Kubernetes, cloud deployment, HTTPS certificates or monitoring setup.
- Full Power BI .pbix artifact. A careful Excel/Power BI lab is provided.
- Production scale guarantees or perfect anonymity.
- Fully executed Maven/Flutter/Express/Mongo/PostgreSQL/PowerShell verification in this environment.

## Important distinction about "no migrations"

PQueue needs no new database migration and its installer never connects to a DB. VoxFlow starts with NEW vf_* collections and indexes in the configured MongoDB. That is intentional initialization, not preservation of the old model in-place. The old rooms collection is kept; legacy import is separate and never automatic.

## Existing platform scaffolding

The authoritative Flutter upload included existing Web/Android files but did not include a complete downloadable Android Gradle-wrapper distribution. The patch preserves your local generated platform folders and does not run flutter create. Browser verification is the first supported local gate. Native Android SDK/Gradle/build/signing and device access require a separate local check; no signed APK is delivered.

## Known operational limitations

PQueue's role authorization is broader than patient/doctor-specific authorization. Historical cohorts can include unresolved visits from earlier days. Full-source snapshot is not an instruction to overwrite your configuration. VoxFlow bounds response count/text; it has no password reset, email verification, shared room administrators or distributed rate limiter. Refresh, not streaming, fetches new feedback. Legacy import is validated before writing but multiple room copies are not one cross-document transaction; an infrastructure failure midway may leave some copied rooms, and rerun skips completed imports safely.
