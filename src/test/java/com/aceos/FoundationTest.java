package com.aceos;

import static org.junit.jupiter.api.Assertions.*;

import com.aceos.database.*;
import com.aceos.model.*;
import com.aceos.security.*;
import com.aceos.service.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Mutates only the explicitly named disposable test schema, never the v1/development database. */
class FoundationTest {
    static final String PASSWORD = "A private local test passphrase";
    static String url;
    AceOsService admin, requester, otherRequester, technician, otherTechnician;

    static Connection operator() throws SQLException {
        return DriverManager.getConnection(
                url,
                System.getenv("ACEOS_TEST_OPERATOR_USER"),
                System.getenv("ACEOS_TEST_OPERATOR_PASSWORD"));
    }

    @BeforeAll
    static void schema() throws Exception {
        url = System.getenv("ACEOS_DB_URL");
        Assumptions.assumeTrue(url != null, "Database checks require isolated test environment");
        if (!url.matches(
                "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/aceos_next_test(?:\\?.*)?"))
            throw new IllegalStateException("Refusing non-test database");
        try (Connection c = operator()) {
            long before;
            try (Statement s = c.createStatement();
                    ResultSet r = s.executeQuery("SELECT COUNT(*) FROM users")) {
                r.next();
                before = r.getLong(1);
            }
            Migrations.migrate(c);
            Migrations.migrate(c);
            try (Statement s = c.createStatement();
                    ResultSet r = s.executeQuery("SELECT COUNT(*) FROM users")) {
                r.next();
                assertEquals(before, r.getLong(1));
            }
        }
    }

    @BeforeEach
    void fixture() throws Exception {
        try (Connection c = operator();
                Statement s = c.createStatement()) {
            for (String table :
                    List.of(
                            "action_audit",
                            "maintenance_records",
                            "work_orders",
                            "complaints",
                            "asset_inspections",
                            "preventive_maintenance_schedules",
                            "user_accounts",
                            "assets",
                            "users")) s.executeUpdate("DELETE FROM " + table);
            String hash = Passwords.hash(PASSWORD);
            for (int id = 1; id <= 6; id++) {
                String role = id <= 2 ? "ADMINISTRATOR" : id <= 4 ? "REQUESTER" : "TECHNICIAN";
                try (PreparedStatement p =
                        c.prepareStatement(
                                "INSERT INTO users(user_id,name,email,role) VALUES (?,?,?,?)")) {
                    p.setInt(1, id);
                    p.setString(2, "User " + id);
                    p.setString(3, "user" + id + "@aceos.local");
                    p.setString(4, role);
                    p.executeUpdate();
                }
                try (PreparedStatement p =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " user_accounts(user_id,login_email,password_hash,state,must_change)"
                                    + " VALUES (?,?,?,'ACTIVE',FALSE)")) {
                    p.setInt(1, id);
                    p.setString(2, "user" + id + "@aceos.local");
                    p.setString(3, hash);
                    p.executeUpdate();
                }
            }
        }
        admin = login(1);
        requester = login(3);
        otherRequester = login(4);
        technician = login(5);
        otherTechnician = login(6);
    }

    AceOsService login(int id) throws Exception {
        AceOsService service = new AceOsService();
        service.signIn("USER" + id + "@ACEOS.LOCAL", PASSWORD);
        return service;
    }

    Asset asset(String code) throws Exception {
        return admin.registerAsset(code, code, "Test", "Local test");
    }

    Complaint complaint(AceOsService who, Asset asset) throws Exception {
        return who.submitComplaint(asset.getAssetId(), "Repair needed", ComplaintPriority.HIGH);
    }

    WorkOrder assigned(Asset asset) throws Exception {
        Complaint c = complaint(requester, asset);
        admin.acceptComplaint(c.getComplaintId());
        return admin.assignWorkOrder(c.getComplaintId(), 5);
    }

    static void denied(org.junit.jupiter.api.function.Executable action) {
        assertThrows(AceOsServiceException.class, action);
    }

    @Test
    void accountCreationActivationResetAndExpiration() throws Exception {
        User user = admin.registerUser("New account", " New@Aceos.Local ", UserRole.REQUESTER);
        denied(() -> new AceOsService().signIn(user.getEmail(), PASSWORD));
        IssuedCredential token = admin.issueCredential(user.getUserId());
        AceOsService fresh = new AceOsService();
        fresh.signIn(user.getEmail(), token.secret());
        assertTrue(fresh.passwordChangeRequired());
        denied(fresh::getAllComplaints);
        fresh.changePassword(token.secret(), PASSWORD);
        assertFalse(fresh.passwordChangeRequired());
        assertTrue(fresh.getAllComplaints().isEmpty());
        denied(() -> new AceOsService().signIn(user.getEmail(), token.secret()));
        AceOsService second = new AceOsService();
        second.signIn(user.getEmail(), PASSWORD);
        IssuedCredential reset = admin.issueCredential(user.getUserId());
        denied(fresh::getAllComplaints);
        denied(second::getAllComplaints);
        try (Connection c = operator();
                PreparedStatement p =
                        c.prepareStatement(
                                "UPDATE user_accounts SET"
                                    + " temporary_expires_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE"
                                    + " user_id=?")) {
            p.setInt(1, user.getUserId());
            p.executeUpdate();
        }
        denied(() -> new AceOsService().signIn(user.getEmail(), reset.secret()));
        denied(() -> admin.registerUser("Duplicate", "new@aceos.local", UserRole.REQUESTER));
        assertTrue(admin.getAccounts().stream().noneMatch(a -> a.toString().contains("argon2")));
    }

    @Test
    void callerOwnershipAndScopedReads() throws Exception {
        Asset a = asset("A"), b = asset("B");
        Complaint own = complaint(requester, a), foreign = complaint(otherRequester, b);
        assertEquals(1, requester.getAllComplaints().size());
        assertEquals(1, requester.getDashboardStatistics().getOpenComplaints());
        denied(() -> requester.getComplaint(foreign.getComplaintId()));
        denied(() -> requester.getUser(4));
        denied(requester::getAllUsers);
        denied(() -> requester.submitComplaint(a.getAssetId(), 4, "Spoof", ComplaintPriority.LOW));
        denied(() -> requester.acceptComplaint(own.getComplaintId()));
        denied(() -> technician.submitComplaint(a.getAssetId(), "Spoof", ComplaintPriority.LOW));
        admin.acceptComplaint(own.getComplaintId());
        WorkOrder w = admin.assignWorkOrder(own.getComplaintId(), 5);
        assertEquals(1, technician.getAllWorkOrders().size());
        assertEquals(0, otherTechnician.getAllWorkOrders().size());
        denied(() -> otherTechnician.getWorkOrder(w.getWorkOrderId()));
        denied(() -> otherTechnician.startWorkOrder(w.getWorkOrderId()));
        denied(() -> admin.startWorkOrder(w.getWorkOrderId()));
        denied(() -> otherTechnician.getAsset(a.getAssetId()));
        denied(() -> new AceOsService().getAllAssets());
        denied(() -> new AceOsService().registerUser("X", "x@x.local", UserRole.ADMINISTRATOR));
        denied(() -> requester.getAllAssetInspections());
    }

    @Test
    void atomicLifecycleAndConcurrentDuplicateCompletion() throws Exception {
        Asset a = asset("LIFE");
        WorkOrder w = assigned(a);
        denied(() -> technician.completeWorkOrder(w.getWorkOrderId(), "Too soon"));
        technician.startWorkOrder(w.getWorkOrderId());
        denied(() -> technician.startWorkOrder(w.getWorkOrderId()));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                attempts.add(
                        pool.submit(
                                () -> {
                                    try {
                                        technician.completeWorkOrder(
                                                w.getWorkOrderId(), "Cable replaced");
                                        return true;
                                    } catch (AceOsServiceException expected) {
                                        return false;
                                    }
                                }));
            assertEquals(1, (attempts.get(0).get() ? 1 : 0) + (attempts.get(1).get() ? 1 : 0));
        } finally {
            pool.shutdown();
        }
        assertEquals(WorkOrderStatus.COMPLETED, admin.getWorkOrder(w.getWorkOrderId()).getStatus());
        assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
        denied(() -> technician.verifyWorkOrder(w.getWorkOrderId()));
        admin.verifyWorkOrder(w.getWorkOrderId());
        assertEquals(
                ComplaintStatus.RESOLVED,
                requester.getComplaint(w.getComplaint().getComplaintId()).getStatus());
        assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
        assertEquals(1, requester.getAssetHistory(a.getAssetId()).size());
        assertTrue(otherRequester.getAssetHistory(a.getAssetId()).isEmpty());
        assertEquals(1, technician.getAssetHistory(a.getAssetId()).size());
        denied(() -> otherTechnician.getAssetHistory(a.getAssetId()));
        try (Connection c = operator();
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT COUNT(*) FROM maintenance_records")) {
            r.next();
            assertEquals(1, r.getInt(1));
        }
    }

    @Test
    void inspectionAssignmentAndDeactivationSafeguards() throws Exception {
        Asset a = asset("INSPECT");
        AssetInspection i =
                admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now());
        assertTrue(technician.getAllAssetInspections().isEmpty());
        admin.assignInspection(i.getInspectionId(), 5);
        assertEquals(
                5, technician.getAssetInspection(i.getInspectionId()).getAssignedTechnicianId());
        denied(
                () ->
                        otherTechnician.completeAssetInspection(
                                i.getInspectionId(),
                                InspectionStatus.PASSED,
                                "Checked",
                                LocalDate.now()));
        denied(
                () ->
                        admin.completeAssetInspection(
                                i.getInspectionId(),
                                InspectionStatus.PASSED,
                                "Checked",
                                LocalDate.now()));
        denied(() -> admin.deactivateAccount(5));
        admin.assignInspection(i.getInspectionId(), 6);
        admin.deactivateAccount(5);
        denied(technician::getAllWorkOrders);
        assertEquals(
                InspectionStatus.PASSED,
                otherTechnician
                        .completeAssetInspection(
                                i.getInspectionId(),
                                InspectionStatus.PASSED,
                                "Checked",
                                LocalDate.now())
                        .getStatus());
        denied(
                () ->
                        otherTechnician.completeAssetInspection(
                                i.getInspectionId(),
                                InspectionStatus.FAILED,
                                "Overwrite",
                                LocalDate.now()));
        IssuedCredential token = admin.reactivateAccount(5);
        AceOsService fresh = new AceOsService();
        fresh.signIn("user5@aceos.local", token.secret());
        assertTrue(fresh.passwordChangeRequired());
    }

    @Test
    void roleChangesLogoutAndLastAdministrator() throws Exception {
        denied(() -> admin.deactivateAccount(1));
        denied(() -> admin.changeAccountRole(1, UserRole.REQUESTER));
        admin.deactivateAccount(2);
        denied(() -> admin.issueCredential(1));
        admin.changeAccountRole(5, UserRole.REQUESTER);
        denied(technician::getAllWorkOrders);
        requester.changePassword(PASSWORD, PASSWORD + " revised");
        AceOsService old = new AceOsService();
        denied(() -> old.signIn("user3@aceos.local", PASSWORD));
        requester.signOut();
        denied(requester::getAllComplaints);
        try (Connection c = operator()) {
            assertThrows(
                    IllegalStateException.class, () -> Bootstrap.activate(c, 1, PASSWORD, false));
        }
        try (Connection c = DatabaseConnection.getConnection()) {
            assertThrows(SQLException.class, () -> Bootstrap.activate(c, 1, PASSWORD, true));
        }
    }

    @Test
    void concurrentAdministratorRemovalCannotRemoveBoth() throws Exception {
        AceOsService second = login(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first =
                    pool.submit(
                            () -> {
                                try {
                                    admin.deactivateAccount(2);
                                    return true;
                                } catch (AceOsServiceException e) {
                                    return false;
                                }
                            });
            Future<Boolean> other =
                    pool.submit(
                            () -> {
                                try {
                                    second.deactivateAccount(1);
                                    return true;
                                } catch (AceOsServiceException e) {
                                    return false;
                                }
                            });
            assertEquals(1, (first.get() ? 1 : 0) + (other.get() ? 1 : 0));
        } finally {
            pool.shutdown();
        }
        try (Connection c = operator();
                Statement s = c.createStatement();
                ResultSet r =
                        s.executeQuery(
                                "SELECT COUNT(*) FROM user_accounts WHERE state='ACTIVE' AND"
                                    + " user_id IN (1,2)")) {
            r.next();
            assertEquals(1, r.getInt(1));
        }
    }

    @Test
    void historicalRecordsSurviveRoleChange() throws Exception {
        Asset a = asset("HISTORY");
        WorkOrder w = assigned(a);
        technician.startWorkOrder(w.getWorkOrderId());
        technician.completeWorkOrder(w.getWorkOrderId(), "Fixed");
        admin.verifyWorkOrder(w.getWorkOrderId());
        AssetInspection i =
                admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now());
        admin.assignInspection(i.getInspectionId(), 5);
        technician.completeAssetInspection(
                i.getInspectionId(), InspectionStatus.PASSED, "Passed", LocalDate.now());
        admin.changeAccountRole(5, UserRole.REQUESTER);
        assertEquals(WorkOrderStatus.VERIFIED, admin.getWorkOrder(w.getWorkOrderId()).getStatus());
        assertEquals(
                InspectionStatus.PASSED, admin.getAssetInspection(i.getInspectionId()).getStatus());
    }

    @Test
    void loginCooldownPersistsAndCanBeUnlocked() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++)
            denied(() -> new AceOsService().signIn("user3@aceos.local", "wrong"));
        denied(() -> new AceOsService().signIn("user3@aceos.local", PASSWORD));
        admin.unlockAccount(3);
        assertNotNull(login(3).signedInUser());
    }

    @Test
    void operatorBootstrapAndRecoveryAreExplicitAndInvalidateSessions() throws Exception {
        try (Connection c = operator();
                Statement s = c.createStatement()) {
            s.executeUpdate(
                    "UPDATE user_accounts SET state='PENDING_ACTIVATION',password_hash=NULL WHERE"
                        + " user_id IN (1,2)");
        }
        denied(admin::getAccounts);
        try (Connection c = operator()) {
            Bootstrap.activate(c, 1, PASSWORD, false);
        }
        AceOsService initial = login(1);
        assertEquals(1, initial.signedInUser().getUserId());
        try (Connection c = operator()) {
            Bootstrap.activate(c, 1, PASSWORD + " recovered", true);
        }
        denied(initial::getAccounts);
        AceOsService recovered = new AceOsService();
        recovered.signIn("user1@aceos.local", PASSWORD + " recovered");
        assertNotNull(recovered.getAccounts());
        assertThrows(
                IllegalArgumentException.class,
                () -> Migrations.requireIsolated("jdbc:mysql://localhost:3306/aceos"));
    }

    @Test
    void migrationRerunPreservesOperationalLinks() throws Exception {
        assertEquals(Migrations.checksum("SELECT 1;\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),Migrations.checksum("SELECT 1;\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Asset a = asset("MIGRATION");
        WorkOrder w = assigned(a);
        AssetInspection i =
                admin.scheduleAssetInspection(
                        a.getAssetId(), "Legacy-like unassigned", LocalDate.now());
        try (Connection c = operator()) {
            Migrations.migrate(c);
        }
        assertEquals(
                a.getAssetId(),
                admin.getWorkOrder(w.getWorkOrderId()).getComplaint().getAsset().getAssetId());
        assertEquals(0, admin.getAssetInspection(i.getInspectionId()).getAssignedTechnicianId());
        assertTrue(technician.getAllAssetInspections().isEmpty());
    }

    @Test
    void reassignmentAndStaleOwner() throws Exception {
        Asset a = asset("ASSIGN");
        WorkOrder w = assigned(a);
        admin.reassignWorkOrder(w.getWorkOrderId(), 6);
        denied(() -> technician.startWorkOrder(w.getWorkOrderId()));
        otherTechnician.startWorkOrder(w.getWorkOrderId());
        denied(() -> admin.reassignWorkOrder(w.getWorkOrderId(), 5));
        denied(() -> admin.deactivateAccount(6));
    }

    @Test
    void migrationMismatchAndInterruptedMigrationStop() throws Exception {
        try (Connection c = operator();
                Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE schema_migrations SET status='STARTED' WHERE version=1");
            try {
                assertThrows(SQLException.class, () -> Migrations.migrate(c));
            } finally {
                s.executeUpdate("UPDATE schema_migrations SET status='COMPLETE' WHERE version=1");
            }
            s.executeUpdate("UPDATE schema_migrations SET checksum=REPEAT('0',64) WHERE version=1");
            try {
                assertThrows(SQLException.class, () -> Migrations.migrate(c));
            } finally {
                byte[] bytes =
                        Migrations.class
                                .getResourceAsStream("/database/V001__accounts.sql")
                                .readAllBytes();
                String checksum = Migrations.checksum(bytes);
                try (PreparedStatement p =
                        c.prepareStatement(
                                "UPDATE schema_migrations SET checksum=? WHERE version=1")) {
                    p.setString(1, checksum);
                    p.executeUpdate();
                }
            }
        }
    }
}
