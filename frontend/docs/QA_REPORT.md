# QA report - exact evidence and limits

## Verdict

Both upgrades are implemented in source and packaged for local verification. They are **release candidates**, not guaranteed bug-free production releases. No Maven, Flutter, real PostgreSQL/MongoDB, actual Express dependency runtime, or PowerShell success is claimed from this environment.

## Checks actually executed here

| Check | Result | Scope / what this does NOT prove |
|---|---|---|
| VoxFlow Node backend tests | 26 passed; 0 failed; 0 skipped | Actual domain/router code, scrypt, sessions, ownership, validation, filtering, CSV and real Node HTTP adapter with IN-MEMORY store. Not Express/Mongo integration. |
| VoxFlow frontend Node utility tests | 5 passed; 0 failed | Formatting/query/stat helper functions, not React browser components. |
| Java 21 parser | 52 source/test files, 0 syntax errors | JavacTask.parse only; does not resolve Maven dependencies, Lombok, Spring beans or SQL. |
| Java behavioral harness | 30 assertions passed; 28 generated SQL calls exercised | Actual AnalyticsService/ReportingMath/QueueStatus with LIGHTWEIGHT Spring/JDBC adapters and canned ResultSets. Checks arithmetic, mapping and SQL fragment/binding boundaries; not PostgreSQL semantics. |
| JS/JSX source parsing/transpilation | 18 modules, 0 syntax/import errors | TypeScript parser and JSX transform; not Vite build or ESLint plugin execution. |
| CSS parser | 2 stylesheets parsed | Syntax only, not visual browser QA. |
| Dart lexical/delimiter/import checks | 9 Dart files, 0 lexical issues | Pygments lexer; NOT flutter analyze, Dart type checking or widget execution. |
| Node syntax checks | All shipped .js source/test/scripts parsed | No missing-dependency or runtime guarantee. |
| Dependency preservation | npm resolved lock entries unchanged; PQueue pom/pubspec/lock unchanged | Root npm name/version/scripts changed; no major dependency upgrades. |
| Configuration preservation | PQueue runtime application.properties byte-identical to uploaded copy and excluded from installer | Local secret values on user's machine were not inspected or needed. |
| Secret/artifact hygiene | No detected private keys, credential-bearing Mongo URI, .env, .git, dumps or signing keys in deliverable sources | Pattern checks are not a substitute for your own final credential review. Test-only JWT fixture is intentional. |

VoxFlow `npm run test:integration` reports **2 skipped tests** without enabling its infrastructure settings. A zero exit code with skipped tests is not an integration pass.

## Prepared but not executed here

- Full `mvn clean test`, including context/schema validation and MockMvc security tests. These need the supplied Spring/Maven dependencies and the local synthetic PostgreSQL setup.
- Dedicated `PostgresIntegrationTest`: actual SQL aggregates, multiple-connection token allocation, row-lock serialization, partial-index behavior, isolated random schemas in a dedicated *_test DB.
- Flutter analyzer, widget/API tests, Chrome build, layout/accessibility and device checks.
- Actual Express middleware integration test (enabled with VOXFLOW_HTTP_TEST=1 after npm ci).
- Actual Mongo store integration test (enabled with MONGO_TEST_URI pointing to *_test).
- Live `smoke-http.js` through running Express and Mongo, and real browser flow against that server.
- PowerShell5.1 execution, DPAPI storage, installer/rollback on Windows, Maven wrapper startup.
- Vite production build and project ESLint rules, Android SDK/Gradle/signing.

## Why these checks are blocked

The environment has Node 22.16.0 and OpenJDK 21.0.12.1, but no Maven executable/cache, Flutter/Dart SDK, PostgreSQL/MongoDB server/client, or PowerShell. npm project dependencies are absent; external registry DNS resolution fails. See environment.log. Versions from your earlier successful Windows logs are preserved in configuration; those historical results are not counted as upgraded-code passes.

## Bugs addressed while reviewing source

- Recovered PQueue report fragment could concatenate :doctorId directly with GROUP BY; separated fragments and exercised both filtered/unfiltered calls.
- Multiple PQueue report HTTP calls could observe inconsistent concurrent data; one repeatable-read snapshot endpoint now supplies the dashboard.
- Queue status updates lacked row serialization; single-row locking added before transition validation.
- Queue timestamps used machine default time while reports used clinic zone; injected clinic clock and server-provided date now align them.
- Generic Flutter request failures and expired sessions left confusing state; safe timeout guidance and authenticated401 navigation added.
- Registration controls needed lifecycle ownership and duplicate-submission guard; dialog owns controllers and pending creation disables another form.
- Feedback baseline exposed all responses by room ID and lacked persisted rooms/owners; new session/owner/public boundary replaces it.
- Feedback creation/closure required atomic consistency; bounded embedded response append checks OPEN/quota in the same write.
- Feedback filtered pagination could strand a page after triage; server clamps requested page to available pages.
- CSV text needs spreadsheet formula neutralization, not just quoting; both are covered by unit tests.

## Local acceptance gates (required before Git push)

1. PQueue installer preview -> no unexpected conflicts -> apply with backups.
2. Full Maven tests -> analyze/test Flutter -> start -> read-only API smoke -> UI UAT/SQL reconciliation.
3. VoxFlow npm ci -> backend checks/tests and frontend tests/lint/build -> run real Express/Mongo -> owner/public/closure/CSV workflow.
4. Execute optional dedicated database tests before claiming real concurrency verified.
5. Review screenshots at desktop/mobile sizes, actual links and privacy behaviors. Save evidence and update README from the real UI.

## Reproducibility

`qa/logs` contains stdout/stderr of executed checks. `qa/tools` includes checking code and the Java adapter harness; inspect the adapters before interpreting their result. Run source-project tests normally on Windows for authoritative framework verification. Archive CRC and source SHA-256 checks are recorded in PACKAGING_QA.json after assembly.
