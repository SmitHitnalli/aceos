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
- Normalized five-table MySQL schema
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

## Database Configuration

AceOS reads its database connection settings from environment variables:

```text
ACEOS_DB_URL
ACEOS_DB_USER
ACEOS_DB_PASSWORD
```

Database credentials must not be stored in the source code or committed to Git.

## Build

From the project directory:

```powershell
mvn compile
```

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
mvn exec:java "-Dexec.mainClass=com.aceos.AceOsServiceTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AceOsServiceValidationTest"
```

The required database environment variables must be available in the terminal before running repository verification.

## Development Status

The domain model, normalized database foundation, JDBC repositories, application service, input validation, database-error translation, JavaFX application shell, user management, asset management, complaint handling, transactional work-order assignment, and work-order start and completion controls are working. The database-backed `Main` program completes the core workflow from complaint submission through verified maintenance history. `AceOsApplication` launches the desktop interface, its completed views call `AceOsService`, and its maintenance view starts and completes work orders while keeping persistence logic in the service layer.

Next development steps:

1. Build administrative work-order verification controls.
2. Connect verification to `AceOsService`.
3. Display verification success, validation, and workflow errors clearly in the interface.
