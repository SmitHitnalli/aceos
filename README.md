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

## Technology Stack

- Java 24
- Maven
- MySQL 8
- JDBC
- MySQL Connector/J
- JavaFX planned for the desktop interface

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

## Run the In-Memory Workflow

```powershell
mvn exec:java "-Dexec.mainClass=com.aceos.Main"
```

## Run Repository Verifications

```powershell
mvn exec:java "-Dexec.mainClass=com.aceos.UserRepositoryTest"
mvn exec:java "-Dexec.mainClass=com.aceos.AssetRepositoryTest"
```

The required database environment variables must be available in the terminal before running repository verification.

## Development Status

The domain model and normalized database foundation are working. JDBC repositories currently support users and assets.

Next development steps:

1. Add complaint persistence and retrieval.
2. Add work-order persistence and retrieval.
3. Add maintenance-record persistence and retrieval.
4. Add transaction handling for the complete maintenance workflow.
5. Build the JavaFX desktop interface.