# ADR-006: Clinical Records, Imaging & Voice Storage Governance (Milestones M4-M5 Design)

**Status**: Proposed (Design Only / Stretch Milestone)
**Date**: October 24, 2026
**Context**:
Future milestones propose adding clinical visit attachments (synthetic PDF lab reports, chest X-ray images) and nurse voice notes transcribed to clinical drafts. Clinical data requires rigorous security boundaries, path traversal prevention, and access controls.

**Decision**:
1. **Never Serve Files Statically**: Do not place clinical files or audio in public web root folders (`/static`, `/public`). All file access must be intermediated through an authenticated, authorization-scoped Spring Boot streaming endpoint.
2. **Path Traversal & Metadata Isolation**:
   - Files are stored on disk or object store using a randomly generated UUID as the filename, completely detached from the user-uploaded filename.
   - Original filename, MIME type, upload timestamp, and owning clinician ID are stored strictly in a `visit_attachment` database metadata table.
   - Content types are validated using magic byte headers, not trusting user file extensions.
3. **Voice Notes Workflow**: Voice recordings must remain synthetic demo audio files; AI transcription drafts must require explicit doctor sign-off before becoming part of any visit summary.

**Alternatives Considered**:
1. *Public Cloud S3 Presigned URLs*: Ideal for production cloud, but for local synthetic portfolio, local secure file storage with controller streaming is preferred to avoid paid cloud accounts.

**Consequences & Tradeoffs**:
- *Positive*: Full audit logging on who accesses clinical files; immune to path traversal (`../../etc/passwd`); compliant with defensive healthcare data design.
- *Negative*: Streaming through Spring Boot consumes JVM buffer memory under large file loads (mitigated by setting 10MB file size ceiling).
