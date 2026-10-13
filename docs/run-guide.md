# Run AceOS — choose your situation

Use **Route 1** on Smit's already configured computer. Use **Route 2** on a new Windows computer with the college ZIP. Use **Route 3** if you want to build the source. Database setup is done once, not every time you run the app.

## Route 1 — Smit's existing computer

Open **PowerShell**, paste this block, and press Enter:

```powershell
Set-Location -LiteralPath 'C:\Smit\aceos'
powershell -NoProfile -ExecutionPolicy Bypass -File '.\.local\start-AceOS-for-Smit.ps1'
```

The private local launcher checks your saved development configuration, starts the existing isolated MySQL instance on **port 13316** if necessary, and opens AceOS. Sign in with your existing AceOS account.

Expected result: the login window opens, then your role dashboard after sign-in. It uses **aceos_next_dev**, not original `aceos` or the disposable test database. No database creation, migration, first-administrator setup or reset is performed.

The launcher is private to this machine; it is intentionally absent from GitHub and release ZIPs. Your passwords stay in the existing private local configuration. Close AceOS normally when finished; do not stop other MySQL services.

## Route 2 — Run the college ZIP on a new Windows computer

**Step 1 — Install the prerequisites.** You need **Java 24** and **MySQL Server 8**. MySQL Workbench is used for the SQL steps below. Maven is not needed for this route. In PowerShell, `java -version` should report Java 24; Workbench should connect to your running local MySQL server.

**Step 2 — Extract the complete college ZIP.** Open PowerShell in the folder containing `app`, `lib`, `source` and `Launch-AceOS.ps1`. For example:

```powershell
Set-Location -LiteralPath 'C:\AceOS\AceOS-v2.0.0'
$aceosClasspath = "$PWD\app\aceos-2.0.0.jar;$PWD\lib\*"
```

Change the folder path to your actual extraction location. Keep this PowerShell window open. Confirm with `Test-Path .\app\aceos-2.0.0.jar`; it should return **True**.

**Step 3 — Complete the one-time database setup below.** Use the packaged classpath already set above. The bundled JAR provides the migration and first-administrator commands; you do not need to compile the source.

**Step 4 — Launch in the same PowerShell window.**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File '.\Launch-AceOS.ps1'
```

Expected result: AceOS opens. Sign in with the **AceOS email/password created in setup Step 5**. The administrator can create requester/technician accounts and issue their private activation credentials.

For later launches, start MySQL, open this extracted folder, set the three runtime connection variables as in setup Step 6 (using the same URL), and run the launcher. Do not repeat the one-time setup.

## Route 3 — Build and run the source

**Step 1 — Install Java 24, MySQL Server 8, MySQL Workbench and Maven.** Check `java -version` and `mvn -version`; Maven must use Java 24.

**Step 2 — Open PowerShell in the source folder containing pom.xml.** For example:

```powershell
Set-Location -LiteralPath 'C:\AceOS\AceOS-v2.0.0-source'
mvn compile
mvn dependency:build-classpath "-Dmdep.outputFile=runtime-classpath.txt"
$aceosClasspath = "$PWD\target\classes;" + (Get-Content -Raw -LiteralPath '.\runtime-classpath.txt').Trim()
```

Change the folder path to your source location. Expected result: both Maven commands show **BUILD SUCCESS**. The temporary classpath file contains library paths, not passwords; delete it when no longer needed.

**Step 3 — Complete the same one-time database setup below**, using `src\main\resources\database\V000__baseline.sql`. If this is Smit's existing configured checkout, use Route 1 instead and do not repeat setup.

**Step 4 — Launch in the same configured PowerShell window.**

```powershell
mvn javafx:run
```

Expected result: AceOS opens and your administrator can sign in. No test database is needed for ordinary source runs. Developer tests/recovery are documented separately.

## One-time setup for a new computer

Do this only for a new, empty database. If you already have a working AceOS installation, use the existing-computer route instead. You do not need a test database just to run the app.

Three logins have different purposes:

| Login | Purpose |
|---|---|
| MySQL administrator, often `root` | Prepare the database once |
| MySQL `aceos_runtime` | Let the app connect with limited permissions |
| AceOS administrator email | Sign in to the app and manage users |

**Step 1 — Create the empty database in MySQL Workbench.** Open your local MySQL connection and run:

```sql
CREATE DATABASE aceos_next_dev CHARACTER SET utf8mb4;
```

Expected result: the new schema appears in Workbench. If it already exists, stop; do not delete it or repeat fresh setup over existing data.

**Step 2 — Add the starting tables in Workbench.** Right-click `aceos_next_dev` in the Schemas sidebar and choose **Set as Default Schema**. Open the SQL file below and execute it while that schema is selected:

- College ZIP: `source\src\main\resources\database\V000__baseline.sql`
- Source checkout: `src\main\resources\database\V000__baseline.sql`

Check with `SHOW TABLES FROM aceos_next_dev;`: you should see the seven starting tables. Do not use `database/schema.sql` or `demo_reset.sql`.

**Step 3 — Finish the schema in the same PowerShell window where you set `$aceosClasspath`.** These examples use local MySQL port 3306. If Workbench shows another port, replace 3306 with that port.

```powershell
$env:ACEOS_MIGRATION_URL = 'jdbc:mysql://localhost:3306/aceos_next_dev?allowPublicKeyRetrieval=true&useSSL=false'
$aceosSetupLogin = Get-Credential -Message 'Enter your MySQL administrator login, not your Windows login'
$env:ACEOS_MIGRATION_USER = $aceosSetupLogin.UserName
$env:ACEOS_MIGRATION_PASSWORD = $aceosSetupLogin.GetNetworkCredential().Password
java -cp $aceosClasspath com.aceos.database.Migrations
```

Expected result: **Foundation schema verified; existing operational data preserved.** Confirm in Workbench:

```sql
SELECT version, status FROM aceos_next_dev.schema_migrations ORDER BY version;
```

You should see versions **1–5**, each **COMPLETE**. The finished schema has 16 tables. Stop and read the error if a command fails; do not continue by changing migration records.

**Step 4 — Create the app's limited database login in Workbench.** Replace the password placeholder privately, then execute this block using your MySQL administrator connection. Do not put the real password in Notion, GitHub or chat.

```sql
CREATE USER 'aceos_runtime'@'localhost' IDENTIFIED BY '<choose a private database password>';

GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.users TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.assets TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.complaints TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.work_orders TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.preventive_maintenance_schedules TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.asset_inspections TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.user_accounts TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.action_audit TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.security_gate TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.asset_safety_holds TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE ON aceos_next_dev.asset_lifecycle_history TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT ON aceos_next_dev.maintenance_records TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT ON aceos_next_dev.complaint_notes TO 'aceos_runtime'@'localhost';
GRANT SELECT, INSERT ON aceos_next_dev.repair_reviews TO 'aceos_runtime'@'localhost';
GRANT SELECT ON aceos_next_dev.schema_migrations TO 'aceos_runtime'@'localhost';
```

Expected result: the commands succeed. The app gets no permission to the operator-only setup table, and cannot update/delete submission, note or review history. If this user already exists, stop and review its existing configuration rather than resetting it.

**Step 5 — Create your first AceOS administrator in PowerShell.** Replace the name and email with your own:

```powershell
java -cp $aceosClasspath com.aceos.database.Bootstrap create "Your name" "you@institution.edu"
```

Enter a new **15–128 character app password** when prompted. It is hidden while you type. Expected result: **Administrator activated. Sign in locally.** This is the app login, not the MySQL password.

**Step 6 — Set the app connection in PowerShell.** Enter the database password you chose for `aceos_runtime`. Use the same MySQL port as Step 3; the example below uses 3306:

```powershell
$env:ACEOS_DB_URL = 'jdbc:mysql://localhost:3306/aceos_next_dev?allowPublicKeyRetrieval=true&useSSL=false'
$env:ACEOS_DB_USER = 'aceos_runtime'
$aceosRuntimeLogin = Get-Credential -UserName 'aceos_runtime' -Message 'Enter the private runtime database password'
$env:ACEOS_DB_PASSWORD = $aceosRuntimeLogin.GetNetworkCredential().Password
Remove-Item Env:ACEOS_MIGRATION_USER, Env:ACEOS_MIGRATION_PASSWORD, Env:ACEOS_MIGRATION_URL -ErrorAction SilentlyContinue
Remove-Variable aceosSetupLogin, aceosRuntimeLogin -ErrorAction SilentlyContinue
```

Return to your chosen route's launch command. These variables last only in this PowerShell window. On later runs, configure only the three runtime variables; do not repeat database setup or administrator bootstrap. Closing the terminal removes its temporary settings.


## If something goes wrong

| What you see | What to do |
|---|---|
| Smit's launcher reports a missing file/library | Keep the existing project folder intact; report the non-secret error. Do not recreate/reset its database. |
| MySQL connection failed | Confirm MySQL is running and the URL uses its correct port: Smit uses 13316; the new-computer example uses 3306. |
| Access denied for database user | Use the MySQL setup login only for migrations/bootstrap; use aceos_runtime for the app. Check the privately entered password and table grants. |
| Java or Maven not found | Install the prerequisite and reopen PowerShell. Check that Maven uses Java 24. |
| Application JAR missing | Extract the complete college ZIP and open its root folder, not just source or docs. |
| Main class not found | Recheck your chosen route's classpath and current folder. College uses app/lib; source uses target/classes plus Maven dependencies. |
| Schema not ready or migrations not COMPLETE | Finish the one-time setup successfully; do not alter migration history or run the legacy reset script. |
| Bootstrap says active administrator exists | Sign in with the existing administrator; bootstrap is not a password reset. |
| App rejects your email/password | Use the AceOS login created in Step 5, not either MySQL login. Avoid repeated guessing; a cooldown may apply. |
| Login needs activation/password replacement | Ask your AceOS administrator for the privately issued temporary credential and complete the first password change. |

## Developer setup and recovery

Only developers running tests need disposable `aceos_next_test`. Use [foundation.md](foundation.md) for table grants, migration internals, existing-data recovery and [verification.md](verification.md) for tests. Tests can replace their disposable fixtures; normal launch must use the development schema.

