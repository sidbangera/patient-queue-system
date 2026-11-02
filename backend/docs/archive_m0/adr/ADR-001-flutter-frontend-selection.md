# ADR-001: Selection of Flutter for Cross-Platform Client Application

**Status**: Accepted
**Date**: October 24, 2026
**Context**:
The Outpatient Patient Queue System requires client interfaces for two primary form factors:
1. Doctors and nurses conducting rounds and consultations, who prefer a handheld mobile app (Android phone/tablet).
2. Front-desk receptionists and clinic administrators managing patient flow, who work on wide-screen desktop workstations via a web browser.

**Decision**:
Use **Flutter** (Dart 3.x) located in `E:\Java SpringBoot\patient-queue-flutter` as a separate sibling application communicating with the Spring Boot backend exclusively via documented JSON REST APIs.

**Alternatives Considered**:
1. *React.js / Web SPA only*: High desktop capability, but lacks native mobile UX and offline/push notification primitives on Android without building a separate mobile codebase.
2. *Separate Native Android (Kotlin) + React Desktop*: Requires maintaining two distinct frontend codebases, doubling development effort for a single developer.

**Consequences & Tradeoffs**:
- *Positive*: Single codebase across Android and Web; unified typed Dart models matching backend DTOs; Material 3 design consistency.
- *Negative*: Flutter Web CanvasKit initial load bundle is larger than minimal HTML/JS; requires explicit CORS support on the backend; token storage differs between Android (`flutter_secure_storage` KeyStore) and Web (`localStorage` / session considerations).
