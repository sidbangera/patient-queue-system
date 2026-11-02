# PQueue 5.0 and VoxFlow 2.0 - installation starts here

This delivery is based on the four current ZIPs you uploaded, not on the older GitHub revisions. It includes the previously unapplied PQueue reporting features plus reliability fixes, and a rewritten VoxFlow application. These are **release candidates requiring the local verification gates below**, not certified production systems.

## Non-negotiable preservation rules

- Your working PQueue folders remain `E:\PQueue\patient-queue-backend` and `E:\PQueue\patient-queue-flutter`.
- Do NOT rerun `00_fresh_install.sql`, `02_existing_database_migration.sql`, the seed script, `flutter create`, or administrator bootstrap.
- The PQueue installer does not touch your runtime `src/main/resources/application.properties`, database, Git history, dependency lockfiles, or secrets. It never connects to PostgreSQL.
- Stop Spring Boot with Ctrl+C and Flutter with q before applying files. Keep the existing terminal open if its environment holds your current JWT key.
- Use a NEW folder for VoxFlow. Its collections are `vf_users`, `vf_sessions`, and `vf_rooms`; the old `rooms` collection is untouched. Old room links need explicit legacy import, not automatic reassignment.
- Do not upload passwords, JWTs, MongoDB URIs with credentials, or password hashes when sharing logs.

## A. Install PQueue first

1. Extract `PQueue_5.0_Source_and_Patch.zip` into `E:\PQueue\pqueue-v5-delivery`. The directory containing `Install-PQueue.ps1` is the extraction root. Avoid an accidental extra nesting level.
2. Preview only:

```powershell
cd 'E:\PQueue\pqueue-v5-delivery'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\Install-PQueue.ps1' -ProjectRoot 'E:\PQueue'
```

`SAME` means the new content already exists, `ADD` is a new file, `REPLACE` matches your uploaded baseline, and `REVIEW` means your current local file differs. REVIEW aborts before any copy; do not work around it with a force overwrite. Share only the listed paths, not secrets.

3. After an acceptable preview, with both apps stopped:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File '.\Install-PQueue.ps1' -ProjectRoot 'E:\PQueue' -Apply
```

All replacements are backed up first under `E:\PQueue\backups\pqueue-v5-<timestamp>-<suffix>`. The installer prints the exact directory. It verifies hashes before and after copying and records every applied file.

4. Backend gate:

```powershell
cd 'E:\PQueue\patient-queue-backend'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\test-backend.ps1'
```

Enter your existing PostgreSQL password. Normal tests use the already migrated **synthetic development database** for context/schema checks and read-only reporting. Dedicated concurrency tests are opt-in and skipped by default. A successful local run must finish with BUILD SUCCESS; skipped dedicated integration tests are not proof of PostgreSQL concurrency.

5. Flutter gate in another terminal:

```powershell
cd 'E:\PQueue\patient-queue-flutter'
flutter pub get
flutter analyze
flutter test
```

The pubspec and lockfile versions have not been changed. Stop if either analysis or tests fails; do not run major dependency upgrades to hide errors. The new tests cover API behavior, the registration dialog, and Insights in addition to the login smoke test.

6. Start backend:

```powershell
cd 'E:\PQueue\patient-queue-backend'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\start-backend.ps1'
```

The script reuses JWT_SECRET from the inherited terminal or a saved DPAPI key. On first use it offers a masked prompt to paste your EXISTING key, or generate a new random key. A new key invalidates old tokens, NOT users or patient records. Do not paste it into chat. The script stores the key encrypted for this Windows user/machine in `%LOCALAPPDATA%\PQueue\jwt-secret.dpapi`; your PostgreSQL password is prompted, not written to source. It also recognizes the older CareFlowOP key file.

If the environment key differs from an existing saved key, startup stops rather than silently rotating it. Keep one deliberate key. Bootstrap admin variables are disabled for this launch.

7. Start Flutter:

```powershell
cd 'E:\PQueue\patient-queue-flutter'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\start-flutter-web.ps1'
```

Default web origin is `http://localhost:5174`, backend `http://localhost:8080`. This keeps VoxFlow's port 5173 separate. The backend script sets explicit allowed origins for port 5174. A manually started Flutter instance on a random port may not match these origins.

8. Read-only API smoke test from a third terminal:

```powershell
cd 'E:\PQueue\patient-queue-backend'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\scripts\smoke-api.ps1'
```

Use your existing clinic_admin credentials. The script checks login, admin profile, snapshot/summary consistency and CSV shape. It does not register patients or create accounts.

9. Sign in through Flutter. Check the date filter (include your October 25 demo records), doctor filter, previous-period comparison, quality panel and Copy CSV. Then follow the UAT checklist before pushing to Git.

### Manual copy alternative

The archive has full files in `source\patient-queue-backend` and `source\patient-queue-flutter`, and a `FILES_TO_CHANGE.md` table. Copy ONLY the rows in that table into the matching paths beneath E:\PQueue. Preserve your runtime properties, .git, Android/Web scaffolding and local credential files. Do not replace the entire folders with the patch or mix in v3/v4 afterward. The installer is safer because it detects unexpected local edits.

### Rollback

Stop both applications. In the printed backup directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File '.\Rollback-PQueue.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\Rollback-PQueue.ps1' -Apply
```

Preview first. Rollback refuses to overwrite files edited after installation. It restores backed-up replacements and removes only newly added patch files. It never rolls back or modifies data. Keep the backup until all tests pass.

## B. Install VoxFlow separately

1. Extract `VoxFlow_2.0_Complete.zip` to `E:\VoxFlow-v2`. Expected subdirectories: `voxflow-backend` and `voxflow-frontend`. Keep old projects/archives untouched. Stop the OLD backend, whose room-reading endpoint did not enforce ownership.
2. Check prerequisites:

```powershell
node --version
npm --version
```

Use Node 22.12 or newer, as required by the uploaded Vite toolchain. No forced dependency-major upgrade was performed. MongoDB must already be accessible, locally or through your own Atlas account. This package does not install MongoDB, Docker, or change database services.

3. Backend:

```powershell
cd 'E:\VoxFlow-v2\voxflow-backend'
Copy-Item '.env.example' '.env'  # First setup only; do not overwrite an existing .env.
notepad .env
npm ci
npm run check
npm test
$env:VOXFLOW_HTTP_TEST = '1'
npm run test:integration
Remove-Item Env:\VOXFLOW_HTTP_TEST
npm start
```

Use your own MONGO_URI in .env. The provided example uses `mongodb://127.0.0.1:27017/voxflow`. Keep FRONTEND_ORIGINS as `http://localhost:5173,http://127.0.0.1:5173` for this local setup. With VOXFLOW_HTTP_TEST=1 the real Express middleware test runs; Mongo integration remains skipped until separately configured. A successful startup must connect to MongoDB before printing the listen message.

4. Frontend, separate terminal:

```powershell
cd 'E:\VoxFlow-v2\voxflow-frontend'
Copy-Item '.env.example' '.env'  # First setup only.
npm ci
npm test
npm run lint
npm run build
npm run dev
```

Open `http://localhost:5173`. Use localhost consistently in both frontend and backend URLs, rather than mixing localhost and 127.0.0.1 for cookie requests. Vite uses strictPort: if 5173 is busy, stop the old instance instead of silently using a different origin. No React token is stored in localStorage.

5. Create a creator account (new authentication system; old feedback had no accounts). Create a room, copy its public link, submit from an incognito window, then refresh the private inbox. Change category/status, filter dates, export CSV, close the room and confirm further submissions are refused.
6. Run the live HTTP smoke test ONLY when you authorize creation of synthetic test data:

```powershell
cd 'E:\VoxFlow-v2\voxflow-backend'
node scripts/smoke-http.js --allow-test-data
```

It creates two synthetic accounts and a room, exercises ownership and submission workflows, then archives that room. It does not delete your other data. Unlike unit tests, this requires the running Express server and MongoDB.

7. Do not import legacy rooms until you read `VOXFLOW_LEGACY_IMPORT.md`. Old ownerless data must be explicitly assigned by a trusted local operator.

## What to send after the first gate

Start with the PQueue installer preview and backend test summary. Share paths marked REVIEW or the first error block, plus the final test summary. Avoid sending thousands of repeated stack-trace lines or credentials. Do not change database schema or reinstall tools in response to a test failure.

## Reality check on verification

The delivery environment did not have Maven dependencies, Flutter/Dart, MongoDB, PostgreSQL, or PowerShell, and dependency downloads were blocked. Node domain/router tests and frontend utility tests ran; Java syntax and selected Java behavioral checks ran with explicitly labeled adapters. Full Spring/Flutter/Express/Mongo/Windows verification must run locally. Read `QA_REPORT.md` for exact results, not inferred success.
