# AceOS

AceOS (Asset, Compliance & Equipment Operations System) is a Java and MySQL academic project for managing institutional assets, maintenance complaints, work orders, and maintenance history.

The initial prototype is inspired by a university-campus environment. It is an independent academic project and is not an official university deployment.

## Core Workflow

```text
Asset
  → Complaint Submission
  → Administrative Acceptance
  → Work-Order Assignment
  → Maintenance Completion
  → Maintenance Record
  → Administrative Verification
```

## Current Features

- Asset and user domain models
- Requester, technician, and administrator roles
- Complaint priorities and workflow statuses
- Work-order assignment and status transitions
- Maintenance-record creation
- Normalized seven-table MySQL schema
- Database constraints and referential integrity
- Environment-based JDBC configuration
- User persistence and retrieval
- Asset persistence and retrieval
- Complaint persistence and relational reconstruction
- Persisted complaint acceptance and rejection decisions
- Work-order persistence with transactional complaint-status updates
- Transactional work-order lifecycle persistence for start, complete, and verify
- Maintenance-record persistence and related work-order reconstruction
- Atomic work-order completion and maintenance-record creation
- Database-backed application service for the complete core workflow
- Application-level input validation
- Clear service errors for invalid workflows and database failures
- JavaFX desktop application shell with section navigation
- Database-backed user registration with clear success, validation, and database-error messages
- Database-backed asset registration with clear success, validation, and database-error messages
- Database-backed complaint submission with ID parsing, role validation, and clear service feedback
- Database-backed administrative complaint acceptance and rejection with workflow feedback
- Transactional work-order assignment with technician-role and complaint-state feedback
- Database-backed work-order start and completion with lifecycle feedback and maintenance-record creation
- Database-backed administrative maintenance verification with role, state, and final workflow feedback
- Presentation-ready dashboard with a clear five-stage maintenance workflow overview
- Database-backed user directory showing IDs, names, emails, and roles with refresh support
- Database-backed asset directory showing IDs, codes, locations, and lifecycle statuses with refresh support
- Database-backed complaint directory showing IDs, related assets, requesters, priorities, and workflow statuses
- Database-backed work-order directory showing assignment details, lifecycle statuses, and latest activity
- Scrollable sidebar navigation that keeps every workflow section accessible at smaller window heights
- Preventive-maintenance schedule model with active/paused state, due-date checks, and recurring-date calculation
- Preventive-maintenance schedule persistence and relational asset reconstruction
- Validated preventive-maintenance schedule creation through the application service
- Persisted preventive-maintenance pause, activation, and recurring completion lifecycle with expected-state protection
- Database-backed preventive-maintenance directory showing schedule IDs, assets, frequencies, due dates, statuses, and timing
- Preventive-maintenance management screen for schedule creation, pausing, reactivation, and recurring completion
- Asset-inspection model with scheduled, passed, and failed states plus overdue and compliance calculations
- Database-backed inspection scheduling, relational retrieval, technician completion, and expected-state protection
- Inspection management and directory screens showing upcoming, overdue, compliant, and non-compliant assets
- Instant client-side search across every directory, with status filters for assets, complaints, work orders, maintenance schedules, and inspections
- Live database-backed dashboard totals for assets, complaints, work orders, overdue schedules, and failed inspections

## Technology Stack

- Java 24
- Maven
- MySQL 8
- JDBC
- MySQL Connector/J
- JavaFX 24.0.2

## Project Structure

```text
aceos/
├── database/
│   ├── schema.sql
│   └── sample_data.sql
├── src/main/java/com/aceos/
│   ├── database/
│   ├── model/
│   ├── repository/
│   ├── service/
│   ├── ui/
│   └── Main.java
├── pom.xml
└── README.md
```

## Prerequisites

- Java 24
- Maven 3.9 or newer
- MySQL 8
- MySQL Workbench or another MySQL client

## Database Setup

1. Open `database/schema.sql` in MySQL Workbench and execute it.
2. Optionally execute `database/sample_data.sql` once to load demonstration data.
3. Create a local MySQL application user with permission to select, insert, and update records in the `aceos` database.

The sample-data script is intended for a fresh database and should not be repeatedly executed against the same data.

## Database Configuration

AceOS reads its database connection settings from environment variables:

```text
ACEOS_DB_URL
ACEOS_DB_USER
ACEOS_DB_PASSWORD
```

Example for the current PowerShell session:

```powershell
$env:ACEOS_DB_URL = "jdbc:mysql://localhost:3306/aceos"
$env:ACEOS_DB_USER = "aceos_app"
$env:ACEOS_DB_PASSWORD = "your local database password"
```

Replace the password locally. Never commit real credentials.

Database credentials must not be stored in the source code or committed to Git.

## Build

From the project directory:

```powershell
mvn clean package
```

## Run the Desktop Application

```powershell
mvn javafx:run
```

The database environment variables must be set in the same terminal before launching AceOS.

## Run the Database-Backed Workflow

```powershell
mvn exec:java "-Dexec.mainClass=com.aceos.Main"
```

## Run Repository Verifications

```powershell
mvn exec:java "-Dexec.mainClass=com.aceos.UserRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AssetRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.ComplaintRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.WorkOrderRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.WorkOrderLifecycleTest"
mvn exec:java "-Dexec.mainClass=com.aceos.MaintenanceRecordRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.PreventiveMaintenanceScheduleRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.PreventiveMaintenanceServiceTest"
mvn exec:java "-Dexec.mainClass=com.aceos.PreventiveMaintenanceLifecycleTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AssetInspectionServiceTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AceOsServiceTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AceOsServiceValidationTest"
```

The required database environment variables must be available in the terminal before running repository verification.

## Development Status

The core AceOS prototype is complete. The domain model, normalized database, JDBC repositories, application service, validation, error translation, and JavaFX interface support the complete workflow from registration and complaint submission through work assignment, maintenance completion, record creation, and administrative verification. The interface keeps business rules and persistence coordination in `AceOsService`, final verification transactionally produces a `VERIFIED` work order, `RESOLVED` complaint, and `ACTIVE` asset, and the dashboard presents the complete workflow clearly. User, asset, complaint, and work-order directories expose every ID and status needed by the workflow forms directly in the interface. Preventive maintenance supports schedule creation, retrieval, pause, activation, recurring completion with persisted due-date advancement, a management screen, and a refreshable directory. Inspection and basic compliance tracking support scheduling, technician pass or fail completion, overdue calculation, compliance results, and JavaFX management and directory screens. The database-backed inspection verification confirms relational reconstruction, technician-role enforcement, protected completion, passed and failed compliance results, and directory retrieval. Every directory supports instant search, while lifecycle-based directories also provide status filtering without additional database calls. The dashboard loads six operational totals through one repository query and allows the user to refresh them without placing JDBC logic in the JavaFX view.

## Future Scope

The current prototype is complete. Optional future extensions include a small summary report for demonstrations and authentication with stronger authorization if AceOS grows beyond its present scope.
