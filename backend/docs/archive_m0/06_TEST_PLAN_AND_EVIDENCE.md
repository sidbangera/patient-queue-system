# Test Plan, Baseline Results & Execution Evidence

**Project Name**: Outpatient Patient Queue Management System 2.0
**Phase**: Milestone M0 Read-Only Baseline Audit
**Execution Date**: October 24, 2026
**Auditor / Runner**: Antigravity Assistant
**Host Environment**: Windows 11, OpenJDK 21.0.8 (JetBrains JBR), Maven 3.9.16, Dart 3.12.2

---

## 1. Test Verification Philosophy & Gate Rules

1. **Honesty & Integrity**: Never claim a test passed without running it. If a test was prevented by environment conditions (e.g. network sandbox, inactive service), it must be explicitly recorded as `BLOCKED` or `NOT RUN`.
2. **Deterministic Baselines**: Code modifications in M1 through M3 must be gated by automated tests. Tests must run before and after every refactoring.
3. **Synthetic Data Only**: All test cases use synthetic patient data (`Synthetic Patient Alpha`, `DOC-01`, etc.). Real names or healthcare records are strictly prohibited.

---

## 2. Milestone M0 Baseline Execution Log

| Test ID | Test Category | Target Component | Command Executed | Expected Output | Actual Output | Status |
|---|---|---|---|---|---|---|
| **TC-ENV-01** | Toolchain Check | Java 21 JDK | `& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -version` | OpenJDK version 21.x detected | `openjdk version "21.0.8" 2025-07-15` | **PASS** |
| **TC-ENV-02** | Toolchain Check | Maven 3.9.x | `mvn -version` (with `JAVA_HOME` pointing to JBR) | Maven 3.9.x output with Java 21 | `Apache Maven 3.9.16 / Java version: 21.0.8` | **PASS** |
| **TC-ENV-03** | Toolchain Check | Dart SDK | `& "E:\dev\flutter\bin\cache\dart-sdk\bin\dart.exe" --version` | Dart 3.x detected | `Dart SDK version: 3.12.2 (stable)` | **PASS** |
| **TC-ENV-04** | Toolchain Check | Flutter SDK | `flutter --version` | Flutter version details | Background process timed out (Flutter script runs git internally outside sandbox) | **BLOCKED / PARTIAL** |
| **TC-ENV-05** | Toolchain Check | PostgreSQL Client & Daemon | `psql --version` & `Get-NetTCPConnection -LocalPort 5432` | PostgreSQL 18.x installed and port 5432 listening | `psql 18.1` installed; port 5432 has no active listener | **PARTIAL** (DB daemon stopped) |
| **TC-BUILD-01** | Backend Compilation | Spring Boot Source | `mvn test-compile` (with Java 21 JBR) | `BUILD SUCCESS` (All 33 source files compile) | `BUILD SUCCESS` (33 main source files, 1 test file compiled in 12.3s) | **PASS** |
| **TC-TEST-01** | Backend Test Execution | Spring Boot Context Test | `mvn test` | Tests execute | `BUILD FAILURE`: Surefire plugin dependencies missing from offline cache; requires network to download `surefire-api:3.5.6` | **BLOCKED (Offline Maven)** |
| **TC-AUTH-01** | Auth Security | Ephemeral JWT Key | Code Inspection (`JwtUtil.java:13`) | Persistent external secret | Detected in-memory key generation `Jwts.SIG.HS256.key().build()` | **FAIL (Design Flaw)** |
| **TC-AUTH-02** | Auth Security | Self-Registration | Code Inspection (`UserController.java:28`) | Restricted role assignment | Anyone can register with `{"role": "ADMIN"}` | **FAIL (Security Vulnerability)** |
| **TC-TOK-01** | Queue Integrity | Token Allocation Concurrency | Code Inspection (`QueueEntryService.java:40`) | Atomic sequence or pessimistic lock | Vulnerable `countByDoctor... + 1` pattern without `@Transactional` | **FAIL (Concurrency Vulnerability)** |

---

## 3. Detailed Command Evidence

### 3.1 Java 21 Verification
```powershell
PS> & "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -version
openjdk version "21.0.8" 2025-07-15
OpenJDK Runtime Environment JBR-21.0.8+1-1038.68-jcef (build 21.0.8+1-b1038.68)
OpenJDK 64-Bit Server VM JBR-21.0.8+1-1038.68-jcef (build 21.0.8+1-b1038.68, mixed mode, sharing)
```

### 3.2 Maven Build Verification
```powershell
PS> $env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr"
PS> mvn test-compile
[INFO] Scanning for projects...
[INFO] -----------< com.sidharth.patientqueue:patient-queue-system >-----------
[INFO] Building  0.0.1-SNAPSHOT
[INFO] Compiling 33 source files with javac [debug parameters release 21] to target\classes
[INFO] Compiling 1 source file with javac [debug parameters release 21] to target\test-classes
[INFO] BUILD SUCCESS
[INFO] Total time:  12.361 s
```

### 3.3 Maven Test Plugin Blocker Evidence
```powershell
PS> mvn test
[INFO] --- surefire:3.5.6:test (default-test) @ patient-queue-system ---
Downloading from central: https://repo.maven.apache.org/maven2/org/apache/maven/surefire/surefire-api/3.5.6/surefire-api-3.5.6.jar
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test (default-test):
Plugin org.apache.maven.plugins:maven-surefire-plugin:3.5.6 or one of its dependencies could not be resolved:
No such host is known (repo.maven.apache.org)
```
*Remediation Plan for M1*: Align the POM's `maven-surefire-plugin` configuration with the locally cached versions (`3.2.5` or `3.1.2`), or resolve dependencies when elevated internet access is granted.

---

## 4. Planned Milestone M1 Test Cases

The following test suites must be developed and validated in M1 before frontend integration:

### 4.1 Unit Test Suite: Queue State Machine (`QueueStatusTest`)
- `testWaitingCanTransitionToInConsultation()` -> Expected: `true`
- `testWaitingCanTransitionToNoShow()` -> Expected: `true`
- `testWaitingCannotTransitionToCompleted()` -> Expected: `false`
- `testInConsultationCanTransitionToCompleted()` -> Expected: `true`
- `testInConsultationCannotTransitionToNoShow()` -> Expected: `false`
- `testCompletedIsTerminal()` -> Expected: `false` for all targets
- `testNoShowIsTerminal()` -> Expected: `false` for all targets

### 4.2 Security Test Suite: JWT & Role RBAC (`JwtSecurityTest`)
- `testTokenValidWithConfiguredSecret()` -> Verify signature verifies across multiple `JwtUtil` instances.
- `testExpiredTokenReturns401()` -> Injects token with expired timestamp; verify HTTP 401.
- `testMalformedTokenReturns401()` -> Injects invalid header string; verify HTTP 401 without 500 error.
- `testDoctorCannotDeletePatient()` -> Role `DOCTOR` hits `DELETE /api/patients/1`; verify HTTP 403.
- `testAdminCanDeletePatient()` -> Role `ADMIN` hits `DELETE /api/patients/1`; verify HTTP 204.
- `testPublicUserCannotRegisterAdmin()` -> Verify role sanitization on `POST /api/users/register`.

### 4.3 Concurrency Test Suite: Daily Token Allocation (`QueueTokenConcurrencyTest`)
- `testConcurrentCheckInsReceiveUniqueTokens()`: 10 parallel threads invoke `checkIn(patientId, doctorId)` for the same doctor simultaneously.
  - Expected: Tokens are strictly unique (`1, 2, 3, 4, 5, 6, 7, 8, 9, 10`). Zero duplicates.
- `testDuplicateActiveCheckInRejected()`: Same patient checked in twice on the same day.
  - Expected: First check-in succeeds with 201; second throws `DuplicateCheckInException` with HTTP 409.
