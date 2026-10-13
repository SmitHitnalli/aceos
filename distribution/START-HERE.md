# AceOS v2.0.0 college package

This Windows package contains the final college application JAR, nine runtime dependency JARs, source snapshot, migrations, documentation and final test evidence. Java 24 and a separate configured MySQL 8 server are required. It includes no database, credentials, account reset or automatic migration.

## Launch an existing configured development database

1. Extract the entire ZIP, keeping `app`, `lib`, `docs`, `source` and the launcher together.
2. Ensure Java 24 is available and your approved isolated MySQL instance is running.
3. Configure `ACEOS_DB_URL`, `ACEOS_DB_USER` and `ACEOS_DB_PASSWORD` privately for the restricted runtime account. The development schema must already have matching COMPLETE V001–V005. Do not paste credentials into this package.
4. Run `Launch-AceOS.ps1` in a local PowerShell window. If Java is not on PATH, use `-JavaPath` with your Java 24 executable.

The launcher creates no accounts and performs no migration/reset. Existing activated development accounts continue to work. Use `source/docs/foundation.md` for fresh isolated setup, table grants, private interactive bootstrap and recovery.

## Contents

- `app/aceos-2.0.0.jar`: verified application classes/resources.
- `lib/`: Maven runtime dependencies, including Windows JavaFX; not a bundled JDK or MySQL server. Library license/notice metadata is retained inside the original JARs.
- `docs/`: report, schema/ER diagrams, OOP/transaction/setup/verification references.
- `source/`: exact frozen Git source tree; no `.git`, `.local`, private configuration, backups or generated classes.
- `verification/`: final JUnit XML/text results and the test summary.
- `PACKAGE-MANIFEST.json`: source identity and per-file SHA-256 checksums.

Final configured checks: 89 tests (46 database, 3 password/session, 40 JavaFX), zero failures/errors/skips. Prior human walkthroughs are Smit-confirmed, distinct from automation. Task 6 was closed at his request; no new demonstration/viva artifacts are claimed.

The existing published v1.0.0 and original `aceos` data remain preserved. Files under `source/database` are labelled v1 baseline; `demo_reset.sql` is destructive and explicitly selects original aceos. Do not run it for current setup. Use V000 plus V001–V005 only on the intended new isolated schema with app windows closed and proven backups.

This is a local college freeze package, not a newly published GitHub release or a deployment installer.
