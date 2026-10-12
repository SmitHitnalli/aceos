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
                            "asset_safety_holds",
                            "asset_lifecycle_history",
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

    @Test
    void registrationConstraintsRolesSessionsAndExactProfile() throws Exception {
        String[] valid = {"R".repeat(50), "N".repeat(100), "C".repeat(100), "L".repeat(150)};
        for (int i = 0; i < 4; i++) {
            String[] input = valid.clone(); input[i] = " ";
            assertThrows(AceOsServiceException.class, () -> admin.registerAsset(input[0], input[1], input[2], input[3]));
            input[i] = valid[i] + "X";
            assertThrows(AceOsServiceException.class, () -> admin.registerAsset(input[0], input[1], input[2], input[3]));
        }
        for (AceOsService denied : List.of(requester, technician, new AceOsService()))
            assertThrows(AceOsServiceException.class, () -> denied.registerAsset("DENIED", "Name", "Category", "Room"));
        assertTrue(admin.getAllAssets().isEmpty());
        Asset created = admin.registerAsset(" " + valid[0] + " ", valid[1], valid[2], valid[3]);
        assertEquals(valid[0], created.getAssetCode());
        assertEquals(AssetStatus.ACTIVE, created.getStatus());
        assertEquals(ManualLifecycle.ACTIVE, created.getManualLifecycle());
        var profile = admin.getAssetProfile(created.getAssetId());
        assertEquals(valid[1], profile.asset().getName());
        assertTrue(profile.holds().isEmpty());
        assertThrows(AceOsServiceException.class, () -> admin.registerAsset(valid[0], "Duplicate", "Category", "Room"));
        assertEquals(1, admin.getAllAssets().size());
        admin.signOut();
        assertThrows(AceOsServiceException.class, () -> admin.registerAsset("LOGGED-OUT", "Name", "Category", "Room"));
        admin = login(1);
        var stale = login(2);
        admin.deactivateAccount(2);
        assertThrows(AceOsServiceException.class, () -> stale.registerAsset("STALE", "Name", "Category", "Room"));
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
    void connectedSchedulesRespectAssignedAssetsRolesAndSessions() throws Exception {
        var a = asset("SCHEDULE-OWN"); var foreign = asset("SCHEDULE-OTHER");
        var own = admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Clean filters", 30, LocalDate.now());
        var other = admin.createPreventiveMaintenanceSchedule(foreign.getAssetId(), "Other task", 7, LocalDate.now());
        assertTrue(technician.getAllPreventiveMaintenanceSchedules().isEmpty());
        assigned(a);
        var i = admin.scheduleAssetInspection(foreign.getAssetId(), "Other assignment", LocalDate.now()); admin.assignInspection(i.getInspectionId(), 6);
        assertEquals(List.of(own.getMaintenanceScheduleId()), technician.getAllPreventiveMaintenanceSchedules().stream().map(PreventiveMaintenanceSchedule::getMaintenanceScheduleId).toList());
        assertEquals(own.getMaintenanceScheduleId(), technician.getPreventiveMaintenanceSchedule(own.getMaintenanceScheduleId()).getMaintenanceScheduleId());
        denied(() -> technician.getPreventiveMaintenanceSchedule(other.getMaintenanceScheduleId()));
        assertEquals(other.getMaintenanceScheduleId(), otherTechnician.getPreventiveMaintenanceSchedule(other.getMaintenanceScheduleId()).getMaintenanceScheduleId());
        assertTrue(requester.getAllPreventiveMaintenanceSchedules().isEmpty()); denied(() -> requester.getPreventiveMaintenanceSchedule(own.getMaintenanceScheduleId()));
        for (var actor : List.of(requester, technician)) {
            denied(() -> actor.createPreventiveMaintenanceSchedule(a.getAssetId(), "Forbidden", 7, LocalDate.now()));
            denied(() -> actor.pausePreventiveMaintenanceSchedule(own.getMaintenanceScheduleId()));
            denied(() -> actor.activatePreventiveMaintenanceSchedule(own.getMaintenanceScheduleId()));
            denied(() -> actor.completePreventiveMaintenanceSchedule(own.getMaintenanceScheduleId(), LocalDate.now()));
        }
        admin.assignInspection(i.getInspectionId(), 5);
        denied(() -> otherTechnician.getPreventiveMaintenanceSchedule(other.getMaintenanceScheduleId()));
        assertEquals(2, technician.getAllPreventiveMaintenanceSchedules().size());
        denied(() -> new AceOsService().getAllPreventiveMaintenanceSchedules());
        technician.signOut(); denied(() -> technician.getPreventiveMaintenanceSchedule(own.getMaintenanceScheduleId()));
    }

    @Test
    void connectedScheduleDatesRecurrenceStateAndAvailabilityRemainGuarded() throws Exception {
        var a = asset("RECURRENCE");
        denied(() -> admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Task", 0, LocalDate.now()));
        denied(() -> admin.createPreventiveMaintenanceSchedule(a.getAssetId(), " ", 30, LocalDate.now()));
        denied(() -> admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Task", 30, LocalDate.now().minusDays(1)));
        denied(() -> admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Task", 30, null));
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Manual hold");
        var s = admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Clean filters", 30, LocalDate.now()); int id = s.getMaintenanceScheduleId();
        denied(() -> admin.completePreventiveMaintenanceSchedule(id, LocalDate.now().plusDays(1)));
        denied(() -> admin.completePreventiveMaintenanceSchedule(id, null));
        assertEquals(LocalDate.now(), admin.getPreventiveMaintenanceSchedule(id).getNextDueDate());
        assertEquals(LocalDate.now().plusDays(30), admin.completePreventiveMaintenanceSchedule(id, LocalDate.now()).getNextDueDate());
        assertEquals(MaintenanceScheduleStatus.ACTIVE, admin.getPreventiveMaintenanceSchedule(id).getStatus());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        admin.pausePreventiveMaintenanceSchedule(id); denied(() -> admin.pausePreventiveMaintenanceSchedule(id));
        denied(() -> admin.completePreventiveMaintenanceSchedule(id, LocalDate.now()));
        assertEquals(LocalDate.now().plusDays(30), admin.activatePreventiveMaintenanceSchedule(id).getNextDueDate());
        denied(() -> admin.activatePreventiveMaintenanceSchedule(id));
        // Preserve the existing completion-date rule, including a recorded past completion.
        assertEquals(LocalDate.now().plusDays(28), admin.completePreventiveMaintenanceSchedule(id, LocalDate.now().minusDays(2)).getNextDueDate());
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.OUT_OF_SERVICE, ManualLifecycle.RETIRED, "Active recurrence"));
        admin.pausePreventiveMaintenanceSchedule(id);
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.OUT_OF_SERVICE, ManualLifecycle.RETIRED, "Finished service");
        denied(() -> admin.activatePreventiveMaintenanceSchedule(id)); denied(() -> admin.completePreventiveMaintenanceSchedule(id, LocalDate.now()));
        denied(() -> admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "New task", 30, LocalDate.now()));
        assertEquals(MaintenanceScheduleStatus.PAUSED, admin.getPreventiveMaintenanceSchedule(id).getStatus());
    }

    @Test
    void assetProfilesPreserveHistoryOwnershipAndRetiredLookupRules() throws Exception {
        Asset a = asset("PROFILE"); var mine = assigned(a);
        technician.startWorkOrder(mine.getWorkOrderId()); technician.completeWorkOrder(mine.getWorkOrderId(), "Own completion");
        admin.verifyWorkOrder(mine.getWorkOrderId());
        var c = complaint(otherRequester, a); admin.acceptComplaint(c.getComplaintId());
        var other = admin.assignWorkOrder(c.getComplaintId(), 6);
        otherTechnician.startWorkOrder(other.getWorkOrderId()); otherTechnician.completeWorkOrder(other.getWorkOrderId(), "Other technical completion");
        admin.verifyWorkOrder(other.getWorkOrderId());
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Manual stop");
        inspection(a, InspectionStatus.FAILED);
        var ownProfile = requester.getAssetProfile(a.getAssetId());
        assertEquals(UserRole.REQUESTER, ownProfile.role()); assertEquals(1, ownProfile.maintenance().size());
        assertEquals(mine.getWorkOrderId(), ownProfile.maintenance().getFirst().workOrderId());
        assertTrue(ownProfile.holds().isEmpty()); assertTrue(ownProfile.lifecycle().isEmpty()); assertTrue(ownProfile.evidence().isEmpty());
        assertEquals(2, technician.getAssetProfile(a.getAssetId()).maintenance().size());
        assertTrue(technician.getAssetProfile(a.getAssetId()).holds().isEmpty());
        denied(() -> technician.getAssetProfile(asset("UNASSIGNED-PROFILE").getAssetId()));
        var all = admin.getAssetProfile(a.getAssetId()); assertEquals(2, all.maintenance().size());
        assertEquals(1, all.holds().size()); assertEquals("Manual stop", all.lifecycle().getFirst().reason());
        assertEquals(1, all.lifecycle().getFirst().actorId());
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.OUT_OF_SERVICE, ManualLifecycle.RETIRED, "Retire finished asset");
        denied(() -> requester.getAssetProfile(a.getAssetId()));
        assertEquals(AssetStatus.RETIRED, technician.getAssetProfile(a.getAssetId()).asset().getStatus());
        // Own historical progress keeps its existing linked lookup; reporting profiles do not broaden it.
        assertEquals(mine.getWorkOrderId(), requester.getWorkDetail(mine.getWorkOrderId()).work().getWorkOrderId());
        denied(() -> new AceOsService().getAssetProfile(a.getAssetId()));
        technician.signOut(); denied(() -> technician.getAssetProfile(a.getAssetId()));
    }

    @Test
    void assetProfileEvidenceExcludesOldForeignBackdatedAndStaleRecords() throws Exception {
        Asset a = asset("PROFILE-EVIDENCE"); inspection(a, InspectionStatus.PASSED);
        var old = assigned(a); technician.startWorkOrder(old.getWorkOrderId());
        technician.completeWorkOrder(old.getWorkOrderId(), "Old correction"); admin.verifyWorkOrder(old.getWorkOrderId());
        inspection(a, InspectionStatus.FAILED); inspection(a, InspectionStatus.FAILED);
        inspection(asset("OTHER-PROFILE-EVIDENCE"), InspectionStatus.PASSED);
        var backdated = admin.scheduleAssetInspection(a.getAssetId(), "Backdated", LocalDate.now());
        admin.assignInspection(backdated.getInspectionId(), 5);
        technician.completeAssetInspection(backdated.getInspectionId(), InspectionStatus.PASSED, "Earlier date", LocalDate.now().minusDays(1));
        var current = assigned(a); technician.startWorkOrder(current.getWorkOrderId());
        technician.completeWorkOrder(current.getWorkOrderId(), "Later correction");
        assertTrue(admin.getAssetProfile(a.getAssetId()).evidence().isEmpty());
        var pass = inspection(a, InspectionStatus.PASSED); admin.verifyWorkOrder(current.getWorkOrderId());
        var profile = admin.getAssetProfile(a.getAssetId()); assertEquals(4, profile.evidence().size());
        assertTrue(profile.evidence().stream().allMatch(e -> e.inspection() ? e.id() == pass.getInspectionId() : e.id() == current.getWorkOrderId()));
        var first = profile.holds().getFirst(); var second = profile.holds().getLast();
        denied(() -> requester.clearSafetyHold(a.getAssetId(), first.holdId(), "Forbidden", pass.getInspectionId(), null));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), first.holdId(), "", pass.getInspectionId(), null));
        admin.clearSafetyHold(a.getAssetId(), first.holdId(), "Later passing inspection", pass.getInspectionId(), null);
        denied(() -> admin.clearSafetyHold(a.getAssetId(), first.holdId(), "Stale hold", pass.getInspectionId(), null));
        var remaining = admin.getAssetProfile(a.getAssetId()); assertEquals(AssetStatus.OUT_OF_SERVICE, remaining.asset().getStatus());
        assertTrue(remaining.evidence().stream().allMatch(e -> e.holdId() == second.holdId()));
        admin.clearSafetyHold(a.getAssetId(), second.holdId(), "Verified later repair", null, current.getWorkOrderId());
        assertTrue(admin.getAssetProfile(a.getAssetId()).evidence().isEmpty());
        assertEquals(AssetStatus.ACTIVE, admin.getAssetProfile(a.getAssetId()).asset().getStatus());
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Stop");
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "Stale lifecycle"));
    }

    @Test
    void roleDashboardScopesQueuesProgressAndSession() throws Exception {
        var a = asset("DASHBOARD");
        var own = complaint(requester, a);
        var foreign = complaint(otherRequester, a);
        var accepted = complaint(requester, a);
        admin.acceptComplaint(accepted.getComplaintId());
        assertEquals(2, admin.getDashboardSummary().counts().get("review"));
        assertEquals(1, admin.getDashboardSummary().counts().get("assignment"));
        var work = admin.assignWorkOrder(accepted.getComplaintId(), 5);
        var due = admin.scheduleAssetInspection(a.getAssetId(), "Overdue", LocalDate.now());
        // Model an inspection scheduled yesterday, without bypassing production scheduling rules.
        try (var connection = operator(); var p = connection.prepareStatement("UPDATE asset_inspections SET scheduled_date=? WHERE inspection_id=?")) {
            p.setDate(1, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
            p.setInt(2, due.getInspectionId()); p.executeUpdate();
        }
        var unrelated = admin.scheduleAssetInspection(a.getAssetId(), "Other technician", LocalDate.now());
        admin.assignInspection(due.getInspectionId(), 5);
        admin.assignInspection(unrelated.getInspectionId(), 6);
        var unassigned = admin.scheduleAssetInspection(a.getAssetId(), "Needs assignment", LocalDate.now());
        var schedule = admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Service", 30, LocalDate.now());
        try (var connection = operator(); var p = connection.prepareStatement("UPDATE preventive_maintenance_schedules SET next_due_date=? WHERE maintenance_schedule_id=?")) {
            p.setDate(1, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
            p.setInt(2, schedule.getMaintenanceScheduleId()); p.executeUpdate();
        }
        assertEquals(1, admin.getDashboardSummary().counts().get("overdueSchedules"));
        admin.pausePreventiveMaintenanceSchedule(schedule.getMaintenanceScheduleId());
        assertEquals(0, admin.getDashboardSummary().counts().get("overdueSchedules"));
        var request = requester.getDashboardSummary();
        assertEquals(2, request.counts().get("open"));
        assertFalse(request.counts().containsKey("review"));
        assertEquals(Set.of(own.getComplaintId(), accepted.getComplaintId()),
                request.items().stream().map(DashboardSummary.Item::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(request.items().stream().anyMatch(i -> i.detail().contains("ASSIGNED")));
        var tech = technician.getDashboardSummary();
        assertEquals(1, tech.counts().get("assigned"));
        assertEquals(1, tech.counts().get("inspections"));
        assertEquals(1, tech.counts().get("overdueInspections"));
        assertEquals(2, tech.items().size());
        assertTrue(otherTechnician.getDashboardSummary().items().stream().noneMatch(i -> i.kind().equals("Work order")));
        assertEquals(1, admin.getDashboardSummary().counts().get("unassignedInspections"));
        technician.startWorkOrder(work.getWorkOrderId());
        assertEquals(1, technician.getDashboardSummary().counts().get("started"));
        technician.completeWorkOrder(work.getWorkOrderId(), "Dashboard repair");
        assertEquals(1, admin.getDashboardSummary().counts().get("verification"));
        assertEquals(1, technician.getDashboardSummary().counts().get("completed"));
        technician.completeAssetInspection(due.getInspectionId(), InspectionStatus.FAILED, "Unsafe", LocalDate.now());
        assertEquals(1, admin.getDashboardSummary().counts().get("holds"));
        assertEquals(0, technician.getDashboardSummary().counts().get("inspections"));
        admin.verifyWorkOrder(work.getWorkOrderId());
        assertEquals(1, requester.getDashboardSummary().counts().get("resolved"));
        assertEquals(0, admin.getDashboardSummary().counts().get("verification"));
        assertEquals(1, admin.getDashboardSummary().counts().get("holds"), "Verification does not clear a safety hold");
        assertTrue(technician.getDashboardSummary().items().isEmpty());
        denied(() -> new AceOsService().getDashboardSummary());
        requester.signOut();
        denied(requester::getDashboardSummary);
    }

    @Test
    void connectedRepairDetailsPreserveOwnershipTimelineAndHolds() throws Exception {
        var a = asset("CONNECTED");
        var own = complaint(requester, a);
        var foreign = complaint(otherRequester, a);
        denied(() -> otherRequester.getRepairDetail(own.getComplaintId()));
        denied(() -> technician.getRepairDetail(own.getComplaintId()));
        assertNull(requester.getRepairDetail(own.getComplaintId()).work());
        assertEquals(1, requester.getRepairDetail(own.getComplaintId()).timeline().size());
        admin.acceptComplaint(own.getComplaintId());
        assertTrue(requester.getRepairDetail(own.getComplaintId()).timeline().stream()
                .anyMatch(e -> e.description().equals("Complaint accepted")));
        denied(() -> admin.acceptComplaint(own.getComplaintId()));
        var w = admin.assignWorkOrder(own.getComplaintId(), 5);
        denied(() -> otherTechnician.getWorkDetail(w.getWorkOrderId()));
        denied(() -> otherRequester.getWorkDetail(w.getWorkOrderId()));
        assertEquals(w.getWorkOrderId(), requester.getRepairDetail(own.getComplaintId()).work().getWorkOrderId());
        assertEquals(own.getComplaintId(), technician.getWorkDetail(w.getWorkOrderId()).complaint().getComplaintId());
        admin.reassignWorkOrder(w.getWorkOrderId(), 6);
        denied(() -> technician.getRepairDetail(own.getComplaintId()));
        assertTrue(otherTechnician.getWorkDetail(w.getWorkOrderId()).timeline().stream()
                .anyMatch(e -> e.description().equals("Repair reassigned")));
        otherTechnician.startWorkOrder(w.getWorkOrderId());
        denied(() -> admin.reassignWorkOrder(w.getWorkOrderId(), 5));
        otherTechnician.completeWorkOrder(w.getWorkOrderId(), "Replaced the damaged cable");
        denied(() -> otherTechnician.completeWorkOrder(w.getWorkOrderId(), "Duplicate"));
        var completed = requester.getWorkDetail(w.getWorkOrderId());
        assertEquals("Replaced the damaged cable", completed.actionTaken());
        assertEquals(WorkOrderStatus.COMPLETED, completed.work().getStatus());
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Independent hold");
        admin.verifyWorkOrder(w.getWorkOrderId());
        denied(() -> admin.verifyWorkOrder(w.getWorkOrderId()));
        var resolved = requester.getRepairDetail(own.getComplaintId());
        assertEquals(ComplaintStatus.RESOLVED, resolved.complaint().getStatus());
        assertEquals(AssetStatus.OUT_OF_SERVICE, resolved.complaint().getAsset().getStatus());
        assertEquals(7, resolved.timeline().size());
        assertEquals(WorkOrderStatus.VERIFIED, resolved.work().getStatus());
        assertNull(otherRequester.getRepairDetail(foreign.getComplaintId()).actionTaken());
        assertTrue(otherRequester.getRepairDetail(foreign.getComplaintId()).timeline().stream()
                .noneMatch(e -> e.description().contains("Repair")));
        var rejected = complaint(otherRequester, a);
        admin.rejectComplaint(rejected.getComplaintId());
        assertTrue(otherRequester.getRepairDetail(rejected.getComplaintId()).timeline().stream()
                .anyMatch(e -> e.description().equals("Complaint rejected")));
        denied(() -> new AceOsService().getRepairDetail(own.getComplaintId()));
        denied(() -> new AceOsService().getWorkDetail(w.getWorkOrderId()));
        requester.signOut(); denied(() -> requester.getRepairDetail(own.getComplaintId()));
    }

    @Test
    void connectedSelectorsExcludeRetiredAndInactiveAccountsAndRevalidateStaleChoices() throws Exception {
        var a = asset("REPORTABLE"); var retired = asset("RETIRED-CHOICE");
        admin.changeAssetLifecycle(retired.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "Unused");
        assertEquals(List.of(a.getAssetId()), requester.getAllAssets().stream().map(Asset::getAssetId).toList());
        denied(() -> complaint(requester, retired));
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Hold");
        var c = complaint(requester, a);
        admin.acceptComplaint(c.getComplaintId());
        assertEquals(Set.of(5, 6), new HashSet<>(admin.getActiveTechnicians().stream().map(User::getUserId).toList()));
        denied(requester::getActiveTechnicians); denied(technician::getActiveTechnicians);
        admin.deactivateAccount(6);
        assertEquals(List.of(5), admin.getActiveTechnicians().stream().map(User::getUserId).toList());
        denied(() -> admin.assignWorkOrder(c.getComplaintId(), 6));
        admin.issueCredential(5);
        assertTrue(admin.getActiveTechnicians().isEmpty());
        denied(() -> admin.assignWorkOrder(c.getComplaintId(), 5));
        denied(() -> new AceOsService().getActiveTechnicians());
        admin.signOut(); denied(admin::getActiveTechnicians);
    }

    @Test
    void dashboardRecentRowsAreBoundedAfterOwnershipFilter() throws Exception {
        var a = asset("RECENT");
        for (int i = 0; i < 8; i++) complaint(requester, a);
        for (int i = 0; i < 8; i++) complaint(otherRequester, a);
        assertEquals(8, requester.getDashboardSummary().counts().get("open"));
        assertEquals(6, requester.getDashboardSummary().items().size());
        var ownIds = requester.getAllComplaints().stream().map(Complaint::getComplaintId).toList();
        assertTrue(requester.getDashboardSummary().items().stream().allMatch(i -> ownIds.contains(i.id())));
        assertEquals(16, admin.getDashboardSummary().counts().get("review"));
        assertEquals(6, admin.getDashboardSummary().items().size());
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
    void connectedInspectionOwnershipStaleCompletionAndIndependentFailureHolds() throws Exception {
        var a = asset("INSPECTION-FLOW");
        var i = admin.scheduleAssetInspection(a.getAssetId(), "Electrical safety", LocalDate.now());
        denied(requester::getAllAssetInspections);
        denied(() -> requester.getAssetInspection(i.getInspectionId()));
        denied(() -> technician.getAssetInspection(i.getInspectionId()));
        admin.assignInspection(i.getInspectionId(), 5);
        assertEquals(1, technician.getAllAssetInspections().size());
        assertTrue(otherTechnician.getAllAssetInspections().isEmpty());
        admin.assignInspection(i.getInspectionId(), 6);
        denied(() -> technician.getAssetInspection(i.getInspectionId()));
        denied(() -> technician.completeAssetInspection(i.getInspectionId(), InspectionStatus.PASSED, "Stale owner", LocalDate.now()));
        otherTechnician.completeAssetInspection(i.getInspectionId(), InspectionStatus.FAILED, "Damaged insulation", LocalDate.now());
        denied(() -> admin.assignInspection(i.getInspectionId(), 5));
        denied(() -> otherTechnician.completeAssetInspection(i.getInspectionId(), InspectionStatus.PASSED, "Stale result", LocalDate.now()));
        assertEquals("Damaged insulation", admin.getAssetInspection(i.getInspectionId()).getNotes());
        assertEquals(1, admin.getSafetyHolds(a.getAssetId()).size());
        inspection(a, InspectionStatus.FAILED);
        inspection(a, InspectionStatus.PASSED);
        assertEquals(2, admin.getSafetyHolds(a.getAssetId()).stream().filter(h -> h.clearedAt() == null).count());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        var pending = admin.scheduleAssetInspection(a.getAssetId(), "Follow-up", LocalDate.now());
        admin.deactivateAccount(5);
        denied(() -> admin.assignInspection(pending.getInspectionId(), 5));
        admin.signOut(); denied(() -> admin.getAssetInspection(pending.getInspectionId()));
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
    AssetInspection inspection(Asset a, InspectionStatus result) throws Exception {
        var i = admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now());
        admin.assignInspection(i.getInspectionId(), 5);
        return technician.completeAssetInspection(i.getInspectionId(), result, "Safety result", LocalDate.now());
    }

    @Test
    void f2OverlappingRepairsInBothCompletionAndVerificationOrders() throws Exception {
        for (boolean reverse : List.of(false, true)) {
            Asset a = asset("OVERLAP" + reverse);
            WorkOrder first = assigned(a), second = assigned(a);
            assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
            technician.startWorkOrder(first.getWorkOrderId());
            technician.startWorkOrder(second.getWorkOrderId());
            var early = reverse ? second : first; var later = reverse ? first : second;
            technician.completeWorkOrder(early.getWorkOrderId(), "First correction");
            admin.verifyWorkOrder(early.getWorkOrderId());
            assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
            assertEquals(ComplaintStatus.RESOLVED, admin.getComplaint(early.getComplaint().getComplaintId()).getStatus());
            technician.completeWorkOrder(later.getWorkOrderId(), "Second correction");
            assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
            admin.verifyWorkOrder(later.getWorkOrderId());
            assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
            assertEquals(2, admin.getAssetHistory(a.getAssetId()).size());
        }
        Asset a = asset("BOTH-COMPLETED");
        var first = assigned(a); var second = assigned(a);
        technician.startWorkOrder(first.getWorkOrderId()); technician.startWorkOrder(second.getWorkOrderId());
        technician.completeWorkOrder(first.getWorkOrderId(), "One"); technician.completeWorkOrder(second.getWorkOrderId(), "Two");
        admin.verifyWorkOrder(second.getWorkOrderId());
        assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
        admin.verifyWorkOrder(first.getWorkOrderId());
        assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
    }

    @Test
    void f2FailureDuringRepairAndIndependentHolds() throws Exception {
        Asset a = asset("HOLDS"); var w = assigned(a);
        technician.startWorkOrder(w.getWorkOrderId());
        var first = inspection(a, InspectionStatus.FAILED); var second = inspection(a, InspectionStatus.FAILED);
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        assertEquals(ManualLifecycle.ACTIVE, admin.getAsset(a.getAssetId()).getManualLifecycle());
        technician.completeWorkOrder(w.getWorkOrderId(), "Corrective repair"); admin.verifyWorkOrder(w.getWorkOrderId());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        var holds = admin.getSafetyHolds(a.getAssetId()); assertEquals(2, holds.size());
        denied(() -> technician.clearSafetyHold(a.getAssetId(), holds.getFirst().holdId(), "Unsafe", null, w.getWorkOrderId()));
        denied(() -> requester.getSafetyHolds(a.getAssetId()));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), holds.getFirst().holdId(), "", null, w.getWorkOrderId()));
        admin.clearSafetyHold(a.getAssetId(), holds.getFirst().holdId(), "Repair verified", null, w.getWorkOrderId());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        var pass = inspection(a, InspectionStatus.PASSED);
        admin.clearSafetyHold(a.getAssetId(), holds.getLast().holdId(), "Reinspection passed", pass.getInspectionId(), null);
        assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
        assertEquals(InspectionStatus.FAILED, admin.getAssetInspection(first.getInspectionId()).getStatus());
        assertEquals(InspectionStatus.FAILED, admin.getAssetInspection(second.getInspectionId()).getStatus());
        var history = admin.getSafetyHolds(a.getAssetId());
        assertEquals(1, history.getFirst().clearedBy()); assertEquals(w.getWorkOrderId(), history.getFirst().verifiedWorkOrderId());
        assertNotNull(history.getLast().clearedAt());
        denied(() -> admin.clearSafetyHold(a.getAssetId(), holds.getLast().holdId(), "Duplicate", pass.getInspectionId(), null));
        assertEquals(2, admin.getDashboardStatistics().getFailedInspections());
    }

    @Test
    void f2UnsupportedEvidenceAndClearanceDuringRepair() throws Exception {
        Asset a = asset("PROOF"); var oldPass = inspection(a, InspectionStatus.PASSED);
        var oldWork = assigned(a); technician.startWorkOrder(oldWork.getWorkOrderId());
        technician.completeWorkOrder(oldWork.getWorkOrderId(), "Old correction"); admin.verifyWorkOrder(oldWork.getWorkOrderId());
        inspection(a, InspectionStatus.FAILED); var h = admin.getSafetyHolds(a.getAssetId()).getFirst();
        var unrelated = inspection(asset("FOREIGN"), InspectionStatus.PASSED);
        var backdated = admin.scheduleAssetInspection(a.getAssetId(), "Backdated result", LocalDate.now());
        admin.assignInspection(backdated.getInspectionId(), 5);
        technician.completeAssetInspection(backdated.getInspectionId(), InspectionStatus.PASSED, "Recorded later, performed earlier", LocalDate.now().minusDays(1));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Backdated", backdated.getInspectionId(), null));
        var w = assigned(a); technician.startWorkOrder(w.getWorkOrderId());
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Old pass", oldPass.getInspectionId(), null));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Old repair", null, oldWork.getWorkOrderId()));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Wrong asset", unrelated.getInspectionId(), null));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Not verified", null, w.getWorkOrderId()));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "No evidence", null, null));
        denied(() -> admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Two kinds", oldPass.getInspectionId(), w.getWorkOrderId()));
        var pass = inspection(a, InspectionStatus.PASSED);
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        admin.clearSafetyHold(a.getAssetId(), h.holdId(), "Passed later", pass.getInspectionId(), null);
        assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
        technician.completeWorkOrder(w.getWorkOrderId(), "Finished"); admin.verifyWorkOrder(w.getWorkOrderId());
        assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
    }

    @Test
    void f2ManualHoldRetirementGuardsAndStaleLifecycle() throws Exception {
        Asset a = asset("MANUAL");
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.OUT_OF_SERVICE, "Manual stop");
        assertEquals(1, requester.getAllAssets().size());
        var w = assigned(a); technician.startWorkOrder(w.getWorkOrderId());
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "Stale"));
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.OUT_OF_SERVICE, ManualLifecycle.RETIRED, "Has job"));
        technician.completeWorkOrder(w.getWorkOrderId(), "Corrected"); admin.verifyWorkOrder(w.getWorkOrderId());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.OUT_OF_SERVICE, ManualLifecycle.ACTIVE, "Return to operation");
        var i = admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now());
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "Has inspection"));
        admin.assignInspection(i.getInspectionId(), 5);
        technician.completeAssetInspection(i.getInspectionId(), InspectionStatus.PASSED, "Passed", LocalDate.now());
        var schedule = admin.createPreventiveMaintenanceSchedule(a.getAssetId(), "Care", 30, LocalDate.now());
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "Active schedule"));
        admin.pausePreventiveMaintenanceSchedule(schedule.getMaintenanceScheduleId());
        var pending = complaint(requester, a);
        admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.ACTIVE, ManualLifecycle.RETIRED, "End of life");
        denied(() -> requester.submitComplaint(a.getAssetId(), "New", ComplaintPriority.LOW));
        denied(() -> admin.acceptComplaint(pending.getComplaintId()));
        denied(() -> admin.scheduleAssetInspection(a.getAssetId(), "New", LocalDate.now()));
        denied(() -> admin.activatePreventiveMaintenanceSchedule(schedule.getMaintenanceScheduleId()));
        denied(() -> admin.changeAssetLifecycle(a.getAssetId(), ManualLifecycle.RETIRED, ManualLifecycle.ACTIVE, "Reopen"));
        assertTrue(requester.getAllAssets().isEmpty());
        assertEquals(1, admin.getAllAssets().size());
        assertEquals(0, admin.getDashboardStatistics().getActiveAssets());
        assertEquals(WorkOrderStatus.VERIFIED, admin.getWorkOrder(w.getWorkOrderId()).getStatus());
    }

    @Test
    void f2ConcurrentVerifyAndInspectionFailureCannotRestoreActive() throws Exception {
        Asset a = asset("RACE"); var w = assigned(a);
        technician.startWorkOrder(w.getWorkOrderId()); technician.completeWorkOrder(w.getWorkOrderId(), "Fixed");
        var i = admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now()); admin.assignInspection(i.getInspectionId(), 5);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var verify = pool.submit(() -> admin.verifyWorkOrder(w.getWorkOrderId()));
            var fail = pool.submit(() -> technician.completeAssetInspection(i.getInspectionId(), InspectionStatus.FAILED, "Unsafe", LocalDate.now()));
            verify.get(); fail.get();
        } finally { pool.shutdown(); }
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
        assertEquals(1, admin.getSafetyHolds(a.getAssetId()).size());
        assertEquals(0, admin.getDashboardStatistics().getActiveAssets());
        assertEquals(AssetStatus.OUT_OF_SERVICE, technician.getAllAssets().getFirst().getStatus());
        assertTrue(otherTechnician.getAllAssets().isEmpty());
    }

    @Test
    void f2FailureHoldAndClearanceRollbackOnLinkedWriteFailure() throws Exception {
        Asset a = asset("ROLLBACK");
        var i = admin.scheduleAssetInspection(a.getAssetId(), "Safety", LocalDate.now()); admin.assignInspection(i.getInspectionId(), 5);
        try (var c = operator(); var st = c.createStatement()) {
            st.execute("ALTER TABLE asset_safety_holds ADD CONSTRAINT f2_reject_hold CHECK (asset_id < 0)");
            try { denied(() -> technician.completeAssetInspection(i.getInspectionId(), InspectionStatus.FAILED, "Unsafe", LocalDate.now())); }
            finally { st.execute("ALTER TABLE asset_safety_holds DROP CHECK f2_reject_hold"); }
        }
        assertEquals(InspectionStatus.SCHEDULED, admin.getAssetInspection(i.getInspectionId()).getStatus());
        assertEquals(AssetStatus.ACTIVE, admin.getAsset(a.getAssetId()).getStatus());
        assertTrue(admin.getSafetyHolds(a.getAssetId()).isEmpty());
        technician.completeAssetInspection(i.getInspectionId(), InspectionStatus.FAILED, "Unsafe", LocalDate.now());
        var pass = inspection(a, InspectionStatus.PASSED); var hold = admin.getSafetyHolds(a.getAssetId()).getFirst();
        try (var c = operator(); var st = c.createStatement()) {
            st.execute("ALTER TABLE assets ADD CONSTRAINT f2_reject_asset CHECK (status <> 'ACTIVE')");
            try { denied(() -> admin.clearSafetyHold(a.getAssetId(), hold.holdId(), "Proof", pass.getInspectionId(), null)); }
            finally { st.execute("ALTER TABLE assets DROP CHECK f2_reject_asset"); }
        }
        assertNull(admin.getSafetyHolds(a.getAssetId()).getFirst().clearedAt());
        assertEquals(AssetStatus.OUT_OF_SERVICE, admin.getAsset(a.getAssetId()).getStatus());
    }

    @Test
    void f2MigrationPreflightAndSecondVersionGuards() throws Exception {
        Asset a = asset("PREFLIGHT");
        try (var c = operator(); var st = c.createStatement()) {
            Migrations.availabilityPreflight(c);
            st.executeUpdate("UPDATE assets SET status='UNDER_MAINTENANCE' WHERE asset_id=" + a.getAssetId());
            var error = assertThrows(SQLException.class, () -> Migrations.availabilityPreflight(c));
            assertTrue(error.getMessage().contains(String.valueOf(a.getAssetId())));
            st.executeUpdate("UPDATE assets SET status='ACTIVE' WHERE asset_id=" + a.getAssetId());
            var w = assigned(a); technician.startWorkOrder(w.getWorkOrderId());
            Migrations.availabilityPreflight(c);
            st.executeUpdate("UPDATE schema_migrations SET status='STARTED' WHERE version=2");
            try { assertThrows(SQLException.class, () -> Migrations.migrate(c)); }
            finally { st.executeUpdate("UPDATE schema_migrations SET status='COMPLETE' WHERE version=2"); }
            st.executeUpdate("UPDATE schema_migrations SET checksum=REPEAT('0',64) WHERE version=2");
            try { assertThrows(SQLException.class, () -> Migrations.migrate(c)); }
            finally {
                try (var p = c.prepareStatement("UPDATE schema_migrations SET checksum=? WHERE version=2")) {
                    p.setString(1, Migrations.checksum(Migrations.class.getResourceAsStream("/database/V002__availability.sql").readAllBytes())); p.executeUpdate();
                }
            }
            Migrations.migrate(c);
        }
        inspection(a, InspectionStatus.FAILED);
        try (var c = operator()) {
            assertTrue(assertThrows(SQLException.class, () -> Migrations.availabilityPreflight(c)).getMessage().contains("Historical failed"));
            Migrations.migrate(c); // Already mapped F2 holds are safe on rerun.
        }
    }

    @Test
    void f2VerificationRollbackPreservesRepairComplaintAndAsset() throws Exception {
        Asset a = asset("VERIFY-ROLLBACK"); var w = assigned(a);
        technician.startWorkOrder(w.getWorkOrderId()); technician.completeWorkOrder(w.getWorkOrderId(), "Fixed");
        try (var c = operator(); var st = c.createStatement()) {
            st.execute("ALTER TABLE complaints ADD CONSTRAINT f2_reject_resolution CHECK (status <> 'RESOLVED')");
            try { denied(() -> admin.verifyWorkOrder(w.getWorkOrderId())); }
            finally { st.execute("ALTER TABLE complaints DROP CHECK f2_reject_resolution"); }
        }
        assertEquals(WorkOrderStatus.COMPLETED, admin.getWorkOrder(w.getWorkOrderId()).getStatus());
        assertEquals(ComplaintStatus.WORK_ORDER_CREATED, admin.getComplaint(w.getComplaint().getComplaintId()).getStatus());
        assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(a.getAssetId()).getStatus());
        assertEquals(1, admin.getAssetHistory(a.getAssetId()).size());
    }

    @Test
    void f2MigratesSupportedLegacyStatesWithoutChangingOperationalIds() throws Exception {
        Asset active = asset("LEGACY-ACTIVE"), busy = asset("LEGACY-BUSY"), out = asset("LEGACY-OUT"), retired = asset("LEGACY-RETIRED");
        var w = assigned(busy); technician.startWorkOrder(w.getWorkOrderId()); technician.completeWorkOrder(w.getWorkOrderId(), "Awaiting verification");
        var pass = inspection(active, InspectionStatus.PASSED);
        try (var c = operator(); var st = c.createStatement()) {
            st.executeUpdate("UPDATE assets SET status='OUT_OF_SERVICE' WHERE asset_id=" + out.getAssetId());
            st.executeUpdate("UPDATE assets SET status='RETIRED' WHERE asset_id=" + retired.getAssetId());
            // Reconstruct V1 only inside the guarded disposable test schema to exercise actual V2 DDL/data mapping.
            st.execute("DROP TABLE asset_safety_holds"); st.execute("DROP TABLE asset_lifecycle_history");
            st.execute("ALTER TABLE assets DROP CHECK chk_manual_lifecycle, DROP COLUMN manual_lifecycle");
            st.execute("ALTER TABLE asset_inspections DROP COLUMN completed_at");
            st.executeUpdate("DELETE FROM schema_migrations WHERE version=2");
            Migrations.migrate(c); Migrations.migrate(c);
        }
        assertEquals(ManualLifecycle.ACTIVE, admin.getAsset(active.getAssetId()).getManualLifecycle());
        assertEquals(ManualLifecycle.ACTIVE, admin.getAsset(busy.getAssetId()).getManualLifecycle());
        assertEquals(AssetStatus.UNDER_MAINTENANCE, admin.getAsset(busy.getAssetId()).getStatus());
        assertEquals(ManualLifecycle.OUT_OF_SERVICE, admin.getAsset(out.getAssetId()).getManualLifecycle());
        assertEquals(ManualLifecycle.RETIRED, admin.getAsset(retired.getAssetId()).getManualLifecycle());
        assertEquals(w.getComplaint().getComplaintId(), admin.getWorkOrder(w.getWorkOrderId()).getComplaint().getComplaintId());
        assertEquals(1, admin.getAssetHistory(busy.getAssetId()).size());
        assertEquals(InspectionStatus.PASSED, admin.getAssetInspection(pass.getInspectionId()).getStatus());
    }

}
