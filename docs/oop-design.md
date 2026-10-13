# OOP design in the final application

This mapping describes the implemented code, not a hypothetical class hierarchy. Source paths below are relative to the repository.

## Encapsulation and lifecycle methods

`model/WorkOrder.java` keeps state/timestamps private. `start`, `complete`, `returnForCorrection`, `resubmitCorrection` and `verify` check valid transitions. Resubmission changes progress without overwriting first completion. `model/Complaint.java` controls acceptance, rejection, withdrawal, reopening and resolution. Services add session, ownership and persistence checks; domain methods alone are not the authorization boundary.

Some simple models, such as `Asset` and `User`, expose setters for reconstruction/legacy use. The report should not claim every entity is immutable or fully protected by private fields alone. Public application changes must use guarded services.

## Composition and associations

`WorkOrder` references its `Complaint` and assigned `User`; a complaint references its `Asset` and reporting `User`. `MaintenanceRecord` references its work order. These are has-a relationships. They do not imply Java object ownership/deletion: database foreign keys and service rules preserve referenced history.

`RepairDetail`, `RepairSubmission`, `TechnicianWorkload`, `AssetProfile` and `MaintenanceSummary` are record projections. They deliver bounded data to views; list-bearing records copy lists where implemented. A record containing another mutable object is not automatically deeply immutable.

## Abstraction and interfaces

`service/AceOsService.java` exposes application actions and translates persistence failures. JavaFX views call services; repositories isolate SQL. `database/Transactions.java` owns a transaction and implements `AutoCloseable`, so try-with-resources rolls back an uncommitted operation and closes its connection.

JDBC uses `Connection`, `PreparedStatement` and `ResultSet` interfaces. Callers work through those abstractions rather than driver implementation classes. `FxTasks.run` accepts `Callable<T>` and callbacks; the private `ServiceOperation<T>` interface defines an authorized operation.

## Inheritance and polymorphism

UI classes extend JavaFX types: `WorkspaceView` extends `BorderPane`, `RepairFlowView` extends `ScrollPane`, and `DashboardView` extends `VBox`. They inherit layout/event behavior and specialize their content. This is the actual inheritance present; there are no Requester/Technician/Administrator subclasses of `User`. Roles use the `UserRole` enum.

JDBC driver objects implement standard interfaces. `Transactions` provides a proxy implementing `Connection` so old repositories can borrow it without committing/closing the outer service transaction. Test preview services extend `AceOsService` and override selected operations to exercise controlled UI success/failure/recovery paths. Test substitution demonstrates overriding; it is not a new runtime role hierarchy.

## Overloading and type safety

Models have overloaded constructors for new versus reconstructed objects. Compatibility service overloads reject mismatched actor IDs, and ID-only repair verification is restricted to an untouched initial submission. Generics in `Callable<T>`, service execution and collection projections retain result types. Enums make lifecycle/role values explicit, with SQL constraints protecting persisted values.

## Sources

All class paths are under `src/main/java/com/aceos/`; controlled overriding examples are in `src/test/java/com/aceos/UiSmokeTest.java`. This mapping is intended for the project report. The dedicated demonstration/viva material is Task 6.
