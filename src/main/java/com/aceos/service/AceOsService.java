package com.aceos.service;

import com.aceos.database.Transactions;
import com.aceos.repository.AvailabilityRepository;
import com.aceos.model.ManualLifecycle;
import com.aceos.model.SafetyHold;
import com.aceos.model.Asset;
import com.aceos.model.AssetInspection;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.DashboardStatistics;
import com.aceos.model.InspectionStatus;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.repository.AssetInspectionRepository;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.DashboardStatisticsRepository;
import com.aceos.repository.MaintenanceRecordRepository;
import com.aceos.repository.MaintenanceWorkflowRepository;
import com.aceos.repository.PreventiveMaintenanceScheduleRepository;
import com.aceos.repository.UserRepository;
import com.aceos.repository.WorkOrderRepository;
import com.aceos.security.*;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AceOsService {
    private volatile Session session;

    public synchronized User signIn(String email, String password) throws AceOsServiceException {
        signOut();
        try {
            session = Authentication.login(email, password);
            return session.user();
        } catch (SQLException | IllegalArgumentException | IllegalStateException e) {
            throw new AceOsServiceException(
                    e instanceof SQLException
                            ? "Unable to connect or schema not ready. Check local database setup."
                            : e.getMessage(),
                    e);
        }
    }

    public synchronized void signOut() {
        if (session != null) session.revoke();
        session = null;
    }

    public User signedInUser() throws AceOsServiceException {
        Session active = session;
        if (active == null || !active.validAt(Instant.now()))
            throw new AceOsServiceException("Sign in to continue");
        return active.user();
    }

    public boolean passwordChangeRequired() {
        return session != null && session.passwordChangeRequired();
    }

    public synchronized void changePassword(String current, String next)
            throws AceOsServiceException {
        try {
            session = Authentication.changePassword(session, current, next);
        } catch (SQLException | IllegalArgumentException | IllegalStateException e) {
            throw new AceOsServiceException(
                    e instanceof SQLException ? "Password could not be changed" : e.getMessage(),
                    e);
        }
    }

    private void requireActor(int id) throws AceOsServiceException {
        if ((Scope.user() == null ? signedInUser() : Scope.user()).getUserId() != id)
            throw new AceOsServiceException("Action identity must match the signed-in account");
    }

    private final UserRepository userRepository = new UserRepository();
    private final AssetRepository assetRepository = new AssetRepository();
    private final ComplaintRepository complaintRepository = new ComplaintRepository();
    private final WorkOrderRepository workOrderRepository = new WorkOrderRepository();
    private final MaintenanceRecordRepository maintenanceRecordRepository =
            new MaintenanceRecordRepository();
    private final MaintenanceWorkflowRepository maintenanceWorkflowRepository =
            new MaintenanceWorkflowRepository();
    private final PreventiveMaintenanceScheduleRepository preventiveMaintenanceScheduleRepository =
            new PreventiveMaintenanceScheduleRepository();
    private final AssetInspectionRepository assetInspectionRepository =
            new AssetInspectionRepository();
    private final DashboardStatisticsRepository dashboardStatisticsRepository =
            new DashboardStatisticsRepository();

    public User registerUser(String name, String email, UserRole role)
            throws AceOsServiceException {
        String validName = requireText(name, "User name", 100);
        String validEmail = requireEmail(email);
        UserRole validRole = requireValue(role, "User role is required");

        return execute(
                UserRole.ADMINISTRATOR,
                "registerUser",
                "The user could not be registered",
                () -> {
                    User created = userRepository.save(new User(validName, validEmail, validRole));
                    SecurityRepository.update(
                            com.aceos.repository.FoundationRepository.INSERT_ACCOUNT,
                            created.getUserId(),
                            created.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
                    return created;
                });
    }

    public Asset registerAsset(String assetCode, String name, String category, String location)
            throws AceOsServiceException {
        String validAssetCode = requireText(assetCode, "Asset code", 50);
        String validName = requireText(name, "Asset name", 100);
        String validCategory = requireText(category, "Asset category", 100);
        String validLocation = requireText(location, "Asset location", 150);

        return execute(
                UserRole.ADMINISTRATOR,
                "registerAsset",
                "The asset could not be registered",
                () ->
                        assetRepository.save(
                                new Asset(
                                        validAssetCode,
                                        validName,
                                        validCategory,
                                        validLocation,
                                        AssetStatus.ACTIVE)));
    }

    public PreventiveMaintenanceSchedule createPreventiveMaintenanceSchedule(
            int assetId, String taskDescription, int frequencyDays, LocalDate nextDueDate)
            throws AceOsServiceException {

        requireId(assetId, "Asset");

        String validTaskDescription = requireText(taskDescription, "Maintenance task", 255);

        if (frequencyDays <= 0) {
            throw new AceOsServiceException("Frequency must be greater than zero");
        }

        LocalDate validNextDueDate = requireValue(nextDueDate, "Next due date is required");

        if (validNextDueDate.isBefore(LocalDate.now())) {
            throw new AceOsServiceException("Next due date cannot be in the past");
        }

        return execute(
                UserRole.ADMINISTRATOR,
                "createPreventiveMaintenanceSchedule",
                "The preventive maintenance schedule " + "could not be created",
                () -> {
                    Asset asset = requireAsset(assetId);
                    AvailabilityRepository.requireOperational(assetId);

                    if (asset.getStatus() == AssetStatus.RETIRED) {
                        throw new IllegalStateException(
                                "A retired asset cannot receive " + "a maintenance schedule");
                    }

                    return preventiveMaintenanceScheduleRepository.save(
                            new PreventiveMaintenanceSchedule(
                                    asset, validTaskDescription, frequencyDays, validNextDueDate));
                });
    }

    public PreventiveMaintenanceSchedule pausePreventiveMaintenanceSchedule(
            int maintenanceScheduleId) throws AceOsServiceException {

        requireId(maintenanceScheduleId, "Maintenance schedule");

        return execute(
                UserRole.ADMINISTRATOR,
                "pausePreventiveMaintenanceSchedule",
                "The maintenance schedule could not be paused",
                () -> {
                    PreventiveMaintenanceSchedule schedule =
                            requirePreventiveMaintenanceSchedule(maintenanceScheduleId);

                    LocalDate expectedDueDate = schedule.getNextDueDate();

                    schedule.pause();

                    preventiveMaintenanceScheduleRepository.updateLifecycle(
                            schedule, MaintenanceScheduleStatus.ACTIVE, expectedDueDate);

                    return requirePreventiveMaintenanceSchedule(maintenanceScheduleId);
                });
    }

    public PreventiveMaintenanceSchedule activatePreventiveMaintenanceSchedule(
            int maintenanceScheduleId) throws AceOsServiceException {

        requireId(maintenanceScheduleId, "Maintenance schedule");

        return execute(
                UserRole.ADMINISTRATOR,
                "activatePreventiveMaintenanceSchedule",
                "The maintenance schedule could not be activated",
                () -> {
                    PreventiveMaintenanceSchedule schedule =
                            requirePreventiveMaintenanceSchedule(maintenanceScheduleId);

                    LocalDate expectedDueDate = schedule.getNextDueDate();

                    AvailabilityRepository.requireOperational(schedule.getAsset().getAssetId());
                    schedule.activate();

                    preventiveMaintenanceScheduleRepository.updateLifecycle(
                            schedule, MaintenanceScheduleStatus.PAUSED, expectedDueDate);

                    return requirePreventiveMaintenanceSchedule(maintenanceScheduleId);
                });
    }

    public PreventiveMaintenanceSchedule completePreventiveMaintenanceSchedule(
            int maintenanceScheduleId, LocalDate completionDate) throws AceOsServiceException {

        requireId(maintenanceScheduleId, "Maintenance schedule");

        LocalDate validCompletionDate = requireValue(completionDate, "Completion date is required");

        if (validCompletionDate.isAfter(LocalDate.now())) {
            throw new AceOsServiceException("Completion date cannot be in the future");
        }

        return execute(
                UserRole.ADMINISTRATOR,
                "completePreventiveMaintenanceSchedule",
                "The maintenance schedule " + "could not be completed",
                () -> {
                    PreventiveMaintenanceSchedule schedule =
                            requirePreventiveMaintenanceSchedule(maintenanceScheduleId);

                    LocalDate expectedDueDate = schedule.getNextDueDate();

                    AvailabilityRepository.requireOperational(schedule.getAsset().getAssetId());
                    schedule.recordCompletion(validCompletionDate);

                    preventiveMaintenanceScheduleRepository.updateLifecycle(
                            schedule, MaintenanceScheduleStatus.ACTIVE, expectedDueDate);

                    return requirePreventiveMaintenanceSchedule(maintenanceScheduleId);
                });
    }

    public PreventiveMaintenanceSchedule getPreventiveMaintenanceSchedule(int maintenanceScheduleId)
            throws AceOsServiceException {

        requireId(maintenanceScheduleId, "Maintenance schedule");

        return execute(
                null,
                "getPreventiveMaintenanceSchedule",
                "The maintenance schedule could not be loaded",
                () -> requirePreventiveMaintenanceSchedule(maintenanceScheduleId));
    }

    public List<PreventiveMaintenanceSchedule> getAllPreventiveMaintenanceSchedules()
            throws AceOsServiceException {

        return execute(
                null,
                "getAllPreventiveMaintenanceSchedules",
                "The maintenance schedules could not be loaded",
                preventiveMaintenanceScheduleRepository::findAll);
    }

    public AssetInspection scheduleAssetInspection(
            int assetId, String inspectionType, LocalDate scheduledDate)
            throws AceOsServiceException {
        requireId(assetId, "Asset");

        String validInspectionType = requireText(inspectionType, "Inspection type", 150);
        LocalDate validScheduledDate = requireValue(scheduledDate, "Scheduled date is required");

        if (validScheduledDate.isBefore(LocalDate.now())) {
            throw new AceOsServiceException("Scheduled date cannot be in the past");
        }

        return execute(
                UserRole.ADMINISTRATOR,
                "scheduleAssetInspection",
                "The inspection could not be scheduled",
                () -> {
                    Asset asset = requireAsset(assetId);
                    AvailabilityRepository.requireOperational(assetId);

                    if (asset.getStatus() == AssetStatus.RETIRED) {
                        throw new IllegalStateException(
                                "A retired asset cannot receive " + "an inspection");
                    }

                    return assetInspectionRepository.save(
                            new AssetInspection(asset, validInspectionType, validScheduledDate));
                });
    }

    public AssetInspection completeAssetInspection(
            int inspectionId,
            int technicianId,
            InspectionStatus result,
            String notes,
            LocalDate completionDate)
            throws AceOsServiceException {
        requireActor(technicianId);
        requireId(inspectionId, "Inspection");
        requireId(technicianId, "Technician");

        InspectionStatus validResult = requireValue(result, "Inspection result is required");

        if (validResult == InspectionStatus.SCHEDULED) {
            throw new AceOsServiceException("Inspection result must be PASSED or FAILED");
        }

        String validNotes = requireText(notes, "Inspection notes", 5000);
        LocalDate validCompletionDate = requireValue(completionDate, "Completion date is required");

        if (validCompletionDate.isAfter(LocalDate.now())) {
            throw new AceOsServiceException("Completion date cannot be in the future");
        }

        return execute(
                UserRole.TECHNICIAN,
                "completeAssetInspection",
                "The inspection could not be completed",
                () -> {
                    requireAssignedInspection(inspectionId);
                    AssetInspection inspection = requireAssetInspection(inspectionId);
                    AvailabilityRepository.requireOperational(inspection.getAsset().getAssetId());
                    requireActor(technicianId);
                    User technician = Scope.user();

                    inspection.complete(technician, validResult, validNotes, validCompletionDate);
                    assetInspectionRepository.updateCompletion(inspection);
                    if (validResult == InspectionStatus.FAILED)
                        AvailabilityRepository.createFailureHold(inspection.getAsset().getAssetId(), inspectionId);
                    else AvailabilityRepository.recalculate(inspection.getAsset().getAssetId());

                    return requireAssetInspection(inspectionId);
                });
    }

    public AssetInspection getAssetInspection(int inspectionId) throws AceOsServiceException {
        requireId(inspectionId, "Inspection");

        return execute(
                null,
                "getAssetInspection",
                "The inspection could not be loaded",
                () -> requireAssetInspection(inspectionId));
    }

    public List<AssetInspection> getAllAssetInspections() throws AceOsServiceException {
        return execute(
                null,
                "getAllAssetInspections",
                "The inspections could not be loaded",
                () -> {
                    SecurityRepository.requireRole(
                            Scope.user(), UserRole.ADMINISTRATOR, UserRole.TECHNICIAN);
                    return assetInspectionRepository.findAll();
                });
    }

    public Complaint submitComplaint(
            int assetId, int requesterId, String description, ComplaintPriority priority)
            throws AceOsServiceException {
        requireActor(requesterId);
        requireId(assetId, "Asset");
        requireId(requesterId, "Requester");

        String validDescription = requireText(description, "Complaint description", 5000);
        ComplaintPriority validPriority = requireValue(priority, "Complaint priority is required");

        return execute(
                UserRole.REQUESTER,
                "submitComplaint",
                "The complaint could not be submitted",
                () -> {
                    Asset asset = requireAsset(assetId);
                    AvailabilityRepository.requireOperational(assetId);
                    if (asset.getStatus() == AssetStatus.RETIRED)
                        throw new IllegalStateException(
                                "A retired asset cannot receive a complaint");
                    requireActor(requesterId);
                    User requester = Scope.user();

                    if (requester.getRole() != UserRole.REQUESTER) {
                        throw new IllegalArgumentException(
                                "Only a requester can submit " + "a complaint");
                    }

                    return complaintRepository.save(
                            new Complaint(asset, requester, validDescription, validPriority));
                });
    }

    public Complaint acceptComplaint(int complaintId, int administratorId)
            throws AceOsServiceException {
        requireActor(administratorId);
        requireId(complaintId, "Complaint");
        requireId(administratorId, "Administrator");

        return execute(
                UserRole.ADMINISTRATOR,
                "acceptComplaint",
                "The complaint could not be accepted",
                () -> {
                    Complaint complaint = requireComplaint(complaintId);
                    AvailabilityRepository.requireOperational(complaint.getAsset().getAssetId());
                    requireActor(administratorId);
                    User administrator = Scope.user();

                    complaint.accept(administrator);
                    complaintRepository.updateReview(complaint);

                    return complaint;
                });
    }

    public Complaint rejectComplaint(int complaintId, int administratorId)
            throws AceOsServiceException {
        requireActor(administratorId);
        requireId(complaintId, "Complaint");
        requireId(administratorId, "Administrator");

        return execute(
                UserRole.ADMINISTRATOR,
                "rejectComplaint",
                "The complaint could not be rejected",
                () -> {
                    Complaint complaint = requireComplaint(complaintId);
                    AvailabilityRepository.requireOperational(complaint.getAsset().getAssetId());
                    requireActor(administratorId);
                    User administrator = Scope.user();

                    complaint.reject(administrator);
                    complaintRepository.updateReview(complaint);

                    return complaint;
                });
    }

    public WorkOrder assignWorkOrder(int complaintId, int technicianId)
            throws AceOsServiceException {
        requireId(complaintId, "Complaint");
        requireId(technicianId, "Technician");

        return execute(
                UserRole.ADMINISTRATOR,
                "assignWorkOrder",
                "The work order could not be assigned",
                () -> {
                    requireActiveTechnician(technicianId);
                    Complaint complaint = requireComplaint(complaintId);
                    AvailabilityRepository.requireOperational(complaint.getAsset().getAssetId());
                    User technician = requireUser(technicianId);

                    return workOrderRepository.save(new WorkOrder(complaint, technician));
                });
    }

    public WorkOrder startWorkOrder(int workOrderId) throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        return execute(
                UserRole.TECHNICIAN,
                "startWorkOrder",
                "The work order could not be started",
                () -> {
                    requireAssignedWork(workOrderId);
                    WorkOrder workOrder = requireWorkOrder(workOrderId);
                    AvailabilityRepository.requireOperational(workOrder.getComplaint().getAsset().getAssetId());

                    workOrder.start();
                    workOrderRepository.updateLifecycle(workOrder);

                    return requireWorkOrder(workOrderId);
                });
    }

    public MaintenanceRecord completeWorkOrder(int workOrderId, String actionTaken)
            throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        String validAction = requireText(actionTaken, "Maintenance action", 5000);

        return execute(
                UserRole.TECHNICIAN,
                "completeWorkOrder",
                "The work order could not be completed",
                () -> {
                    requireAssignedWork(workOrderId);
                    WorkOrder workOrder = requireWorkOrder(workOrderId);
                    AvailabilityRepository.requireOperational(workOrder.getComplaint().getAsset().getAssetId());

                    return maintenanceWorkflowRepository.completeWithMaintenanceRecord(
                            workOrder, validAction);
                });
    }

    public WorkOrder verifyWorkOrder(int workOrderId, int administratorId)
            throws AceOsServiceException {
        requireActor(administratorId);
        requireId(workOrderId, "Work order");
        requireId(administratorId, "Administrator");

        return execute(
                UserRole.ADMINISTRATOR,
                "verifyWorkOrder",
                "The work order could not be verified",
                () -> {
                    WorkOrder workOrder = requireWorkOrder(workOrderId);
                    AvailabilityRepository.requireOperational(workOrder.getComplaint().getAsset().getAssetId());
                    requireActor(administratorId);
                    User administrator = Scope.user();

                    workOrder.verify(administrator);
                    workOrderRepository.updateLifecycle(workOrder);

                    return requireWorkOrder(workOrderId);
                });
    }

    public DashboardStatistics getDashboardStatistics() throws AceOsServiceException {
        return execute(
                null,
                "getDashboardStatistics",
                "The dashboard statistics could not be loaded",
                this::scopedStatistics);
    }

    public com.aceos.model.DashboardSummary getDashboardSummary() throws AceOsServiceException {
        return execute(null, "getDashboardSummary", "Your dashboard could not be loaded",
                () -> new com.aceos.repository.DashboardSummaryRepository().load());
    }

    public User getUser(int userId) throws AceOsServiceException {
        requireId(userId, "User");

        return execute(
                null,
                "getUser",
                "The user could not be loaded",
                () -> {
                    if (Scope.user().getRole() != UserRole.ADMINISTRATOR
                            && Scope.user().getUserId() != userId)
                        throw new IllegalStateException("Record not available");
                    return requireUser(userId);
                });
    }

    public List<User> getAllUsers() throws AceOsServiceException {
        return execute(
                UserRole.ADMINISTRATOR,
                "getAllUsers",
                "The users could not be loaded",
                userRepository::findAll);
    }

    public Asset getAsset(int assetId) throws AceOsServiceException {
        requireId(assetId, "Asset");

        return execute(
                null, "getAsset", "The asset could not be loaded", () -> requireAsset(assetId));
    }

    public com.aceos.model.AssetProfile getAssetProfile(int assetId) throws AceOsServiceException {
        requireId(assetId, "Asset");
        return execute(null, "getAssetProfile", "Asset profile could not be loaded", () -> {
            Asset asset = requireAsset(assetId);
            boolean admin = Scope.user().getRole() == UserRole.ADMINISTRATOR;
            return new com.aceos.model.AssetProfile(asset, Scope.user().getRole(),
                    new com.aceos.repository.FoundationRepository().history(assetId),
                    admin ? AvailabilityRepository.holds(assetId) : List.of(),
                    admin ? AvailabilityRepository.lifecycleHistory(assetId) : List.of(),
                    admin ? AvailabilityRepository.clearanceEvidence(assetId) : List.of());
        });
    }

    public List<Asset> getAllAssets() throws AceOsServiceException {
        return execute(
                null, "getAllAssets", "The assets could not be loaded", assetRepository::findAll);
    }

    public Asset changeAssetLifecycle(int assetId, ManualLifecycle expected, ManualLifecycle next, String reason)
            throws AceOsServiceException {
        requireId(assetId, "Asset");
        requireValue(expected, "Current lifecycle required");
        requireValue(next, "New lifecycle required");
        String validReason = requireText(reason, "Lifecycle reason", 1000);
        return execute(UserRole.ADMINISTRATOR, "changeAssetLifecycle", "Lifecycle could not be changed", () -> {
            requireAsset(assetId);
            AvailabilityRepository.changeLifecycle(assetId, expected, next, validReason);
            return requireAsset(assetId);
        });
    }

    public List<SafetyHold> getSafetyHolds(int assetId) throws AceOsServiceException {
        requireId(assetId, "Asset");
        return execute(UserRole.ADMINISTRATOR, "getSafetyHolds", "Holds could not be loaded", () -> {
            requireAsset(assetId);
            return AvailabilityRepository.holds(assetId);
        });
    }

    public Asset clearSafetyHold(int assetId, int holdId, String reason, Integer passingInspectionId, Integer verifiedWorkOrderId)
            throws AceOsServiceException {
        requireId(assetId, "Asset"); requireId(holdId, "Hold");
        String validReason = requireText(reason, "Clearance reason", 1000);
        if (passingInspectionId != null) requireId(passingInspectionId, "Passing inspection");
        if (verifiedWorkOrderId != null) requireId(verifiedWorkOrderId, "Verified work order");
        return execute(UserRole.ADMINISTRATOR, "clearSafetyHold", "Hold could not be cleared", () -> {
            requireAsset(assetId);
            AvailabilityRepository.clearHold(assetId, holdId, validReason, passingInspectionId, verifiedWorkOrderId);
            return requireAsset(assetId);
        });
    }

    public Complaint getComplaint(int complaintId) throws AceOsServiceException {
        requireId(complaintId, "Complaint");

        return execute(
                null,
                "getComplaint",
                "The complaint could not be loaded",
                () -> requireComplaint(complaintId));
    }

    public List<Complaint> getAllComplaints() throws AceOsServiceException {
        return execute(
                null,
                "getAllComplaints",
                "The complaints could not be loaded",
                complaintRepository::findAll);
    }

    public List<User> getActiveTechnicians() throws AceOsServiceException {
        return execute(UserRole.ADMINISTRATOR, "getActiveTechnicians", "Technicians could not be loaded",
                () -> new com.aceos.repository.RepairFlowRepository().activeTechnicians());
    }

    public com.aceos.model.RepairDetail getRepairDetail(int complaintId) throws AceOsServiceException {
        requireId(complaintId, "Complaint");
        return execute(null, "getRepairDetail", "Complaint progress could not be loaded",
                () -> new com.aceos.repository.RepairFlowRepository().detail(requireComplaint(complaintId)));
    }

    public com.aceos.model.RepairDetail getWorkDetail(int workOrderId) throws AceOsServiceException {
        requireId(workOrderId, "Work order");
        return execute(null, "getWorkDetail", "Repair progress could not be loaded", () -> {
            var work = requireWorkOrder(workOrderId);
            return new com.aceos.repository.RepairFlowRepository().detail(work.getComplaint());
        });
    }

    public WorkOrder getWorkOrder(int workOrderId) throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        return execute(
                null,
                "getWorkOrder",
                "The work order could not be loaded",
                () -> requireWorkOrder(workOrderId));
    }

    public List<WorkOrder> getAllWorkOrders() throws AceOsServiceException {
        return execute(
                null,
                "getAllWorkOrders",
                "The work orders could not be loaded",
                workOrderRepository::findAll);
    }

    public MaintenanceRecord getMaintenanceRecord(int maintenanceRecordId)
            throws AceOsServiceException {
        requireId(maintenanceRecordId, "Maintenance record");

        return execute(
                null,
                "getMaintenanceRecord",
                "The maintenance record could not be loaded",
                () ->
                        maintenanceRecordRepository
                                .findById(maintenanceRecordId)
                                .orElseThrow(
                                        () ->
                                                new AceOsServiceException(
                                                        "Maintenance record not found: "
                                                                + maintenanceRecordId)));
    }

    public Complaint submitComplaint(int assetId, String description, ComplaintPriority priority)
            throws AceOsServiceException {
        return submitComplaint(assetId, signedInUser().getUserId(), description, priority);
    }

    public Complaint acceptComplaint(int id) throws AceOsServiceException {
        return acceptComplaint(id, signedInUser().getUserId());
    }

    public Complaint reviewComplaint(int id, boolean accept) throws AceOsServiceException {
        return accept ? acceptComplaint(id) : rejectComplaint(id);
    }

    public PreventiveMaintenanceSchedule setScheduleActive(int id, boolean active)
            throws AceOsServiceException {
        return active
                ? activatePreventiveMaintenanceSchedule(id)
                : pausePreventiveMaintenanceSchedule(id);
    }

    public Complaint rejectComplaint(int id) throws AceOsServiceException {
        return rejectComplaint(id, signedInUser().getUserId());
    }

    public WorkOrder verifyWorkOrder(int id) throws AceOsServiceException {
        return verifyWorkOrder(id, signedInUser().getUserId());
    }

    public AssetInspection completeAssetInspection(
            int id, InspectionStatus result, String notes, LocalDate date)
            throws AceOsServiceException {
        return completeAssetInspection(id, signedInUser().getUserId(), result, notes, date);
    }

    private void requireActiveTechnician(int id) throws SQLException {
        var a = SecurityRepository.account(null, id);
        if (a == null
                || a.user().getRole() != UserRole.TECHNICIAN
                || !"ACTIVE".equals(a.state())
                || a.mustChange()) throw new IllegalStateException("Choose an active technician");
    }

    private void requireAssignedWork(int id) throws SQLException {
        if (SecurityRepository.count(
                        com.aceos.repository.FoundationRepository.COUNT_ASSIGNED_WORK,
                        id,
                        Scope.user().getUserId())
                != 1) throw new IllegalStateException("Record not available");
    }

    private void requireAssignedInspection(int id) throws SQLException {
        if (SecurityRepository.count(
                        com.aceos.repository.FoundationRepository.COUNT_ASSIGNED_INSPECTION,
                        id,
                        Scope.user().getUserId())
                != 1) throw new IllegalStateException("Record not available");
    }

    private DashboardStatistics scopedStatistics() throws SQLException {
        if (Scope.user().getRole() == UserRole.ADMINISTRATOR)
            return dashboardStatisticsRepository.load();
        return new com.aceos.repository.FoundationRepository().statistics();
    }

    public java.util.List<AccountSummary> getAccounts() throws AceOsServiceException {
        return execute(
                UserRole.ADMINISTRATOR,
                "getAccounts",
                "Accounts could not be loaded",
                () -> new com.aceos.repository.FoundationRepository().accounts());
    }

    public java.util.List<MaintenanceSummary> getAssetHistory(int assetId)
            throws AceOsServiceException {
        return execute(
                null,
                "getAssetHistory",
                "Maintenance history could not be loaded",
                () -> {
                    requireAsset(assetId);
                    return new com.aceos.repository.FoundationRepository().history(assetId);
                });
    }

    public IssuedCredential issueCredential(int id) throws AceOsServiceException {
        return execute(
                UserRole.ADMINISTRATOR,
                "issueCredential",
                "Credential could not be issued",
                () -> Authentication.issue(Scope.user(), id));
    }

    private void requireNoAssignments(int id) throws SQLException {
        if (SecurityRepository.count(
                                com.aceos.repository.FoundationRepository.COUNT_UNFINISHED_WORK, id)
                        > 0
                || SecurityRepository.count(
                                com.aceos.repository.FoundationRepository
                                        .COUNT_SCHEDULED_INSPECTIONS,
                                id)
                        > 0)
            throw new IllegalStateException(
                    "Reassign or finish outstanding jobs and inspections first");
    }

    private void protectAdministrator(SecurityRepository.Account a) throws SQLException {
        if (a.user().getUserId() == Scope.user().getUserId())
            throw new IllegalStateException("You cannot deactivate or change your own role");
        if (a.user().getRole() == UserRole.ADMINISTRATOR
                && "ACTIVE".equals(a.state())
                && !a.mustChange()
                && SecurityRepository.count(
                                com.aceos.repository.FoundationRepository
                                        .COUNT_ACTIVE_ADMINISTRATORS)
                        <= 1)
            throw new IllegalStateException("Keep at least one active administrator");
    }

    public void deactivateAccount(int id) throws AceOsServiceException {
        execute(
                UserRole.ADMINISTRATOR,
                "deactivateAccount",
                "Account could not be deactivated",
                () -> {
                    var a = SecurityRepository.account(null, id);
                    if (a == null) throw new IllegalArgumentException("Account not found");
                    protectAdministrator(a);
                    requireNoAssignments(id);
                    SecurityRepository.update(
                            com.aceos.repository.FoundationRepository.DISABLE_ACCOUNT, id);
                    SecurityRepository.audit(Scope.user().getUserId(), "ACCOUNT_DISABLED", id);
                    return null;
                });
    }

    public IssuedCredential reactivateAccount(int id) throws AceOsServiceException {
        return execute(
                UserRole.ADMINISTRATOR,
                "reactivateAccount",
                "Account could not be reactivated",
                () -> {
                    var a = SecurityRepository.account(null, id);
                    if (a == null || !"DISABLED".equals(a.state()))
                        throw new IllegalArgumentException("Choose a disabled account");
                    return Authentication.issueUnchecked(
                            Scope.user().getUserId(), id, "ACCOUNT_REACTIVATED");
                });
    }

    public void changeAccountRole(int id, UserRole role) throws AceOsServiceException {
        execute(
                UserRole.ADMINISTRATOR,
                "changeAccountRole",
                "Role could not be changed",
                () -> {
                    if (role == null) throw new IllegalArgumentException("Choose a role");
                    var a = SecurityRepository.account(null, id);
                    if (a == null) throw new IllegalArgumentException("Account not found");
                    protectAdministrator(a);
                    requireNoAssignments(id);
                    SecurityRepository.update(
                            com.aceos.repository.FoundationRepository.CHANGE_ROLE, role.name(), id);
                    SecurityRepository.update(
                            com.aceos.repository.FoundationRepository.INVALIDATE_SESSIONS, id);
                    SecurityRepository.audit(Scope.user().getUserId(), "ROLE_CHANGED", id);
                    return null;
                });
    }

    public void unlockAccount(int id) throws AceOsServiceException {
        execute(
                UserRole.ADMINISTRATOR,
                "unlockAccount",
                "Account could not be unlocked",
                () -> {
                    SecurityRepository.update(
                            com.aceos.repository.FoundationRepository.UNLOCK_ACCOUNT, id);
                    SecurityRepository.audit(Scope.user().getUserId(), "ACCOUNT_UNLOCKED", id);
                    return null;
                });
    }

    public void reassignWorkOrder(int id, int technician) throws AceOsServiceException {
        execute(
                UserRole.ADMINISTRATOR,
                "reassignWorkOrder",
                "Work could not be reassigned",
                () -> {
                    requireActiveTechnician(technician);
                    if (SecurityRepository.update(
                                    com.aceos.repository.FoundationRepository
                                            .REASSIGN_UNSTARTED_WORK,
                                    technician,
                                    id)
                            != 1)
                        throw new IllegalStateException(
                                "Only assigned, unstarted work can be reassigned");
                    SecurityRepository.audit(Scope.user().getUserId(), "WORK_REASSIGNED", id);
                    return null;
                });
    }

    public void assignInspection(int id, int technician) throws AceOsServiceException {
        execute(
                UserRole.ADMINISTRATOR,
                "assignInspection",
                "Inspection could not be assigned",
                () -> {
                    requireActiveTechnician(technician);
                    if (SecurityRepository.update(
                                    com.aceos.repository.FoundationRepository
                                            .ASSIGN_SCHEDULED_INSPECTION,
                                    technician,
                                    id)
                            != 1)
                        throw new IllegalStateException(
                                "Only scheduled inspections can be assigned");
                    SecurityRepository.audit(Scope.user().getUserId(), "INSPECTION_ASSIGNED", id);
                    return null;
                });
    }

    private User requireUser(int userId) throws SQLException, AceOsServiceException {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new AceOsServiceException("User not found: " + userId));
    }

    private Asset requireAsset(int assetId) throws SQLException, AceOsServiceException {
        return assetRepository
                .findById(assetId)
                .orElseThrow(() -> new AceOsServiceException("Asset not found: " + assetId));
    }

    private PreventiveMaintenanceSchedule requirePreventiveMaintenanceSchedule(
            int maintenanceScheduleId) throws SQLException, AceOsServiceException {

        return preventiveMaintenanceScheduleRepository
                .findById(maintenanceScheduleId)
                .orElseThrow(
                        () ->
                                new AceOsServiceException(
                                        "Maintenance schedule not found: "
                                                + maintenanceScheduleId));
    }

    private AssetInspection requireAssetInspection(int inspectionId)
            throws SQLException, AceOsServiceException {
        return assetInspectionRepository
                .findById(inspectionId)
                .orElseThrow(
                        () -> new AceOsServiceException("Inspection not found: " + inspectionId));
    }

    private Complaint requireComplaint(int complaintId) throws SQLException, AceOsServiceException {
        return complaintRepository
                .findById(complaintId)
                .orElseThrow(
                        () -> new AceOsServiceException("Complaint not found: " + complaintId));
    }

    private WorkOrder requireWorkOrder(int workOrderId) throws SQLException, AceOsServiceException {
        return workOrderRepository
                .findById(workOrderId)
                .orElseThrow(
                        () -> new AceOsServiceException("Work order not found: " + workOrderId));
    }

    private static String requireText(String value, String fieldName, int maximumLength)
            throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException(fieldName + " is required");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new AceOsServiceException(
                    fieldName + " must not exceed " + maximumLength + " characters");
        }

        return normalizedValue;
    }

    private static String requireEmail(String email) throws AceOsServiceException {
        String validEmail = requireText(email, "Email", 255);

        int atIndex = validEmail.indexOf('@');
        int finalDotIndex = validEmail.lastIndexOf('.');

        if (atIndex <= 0
                || atIndex != validEmail.lastIndexOf('@')
                || finalDotIndex <= atIndex + 1
                || finalDotIndex == validEmail.length() - 1
                || validEmail.chars().anyMatch(Character::isWhitespace)) {
            throw new AceOsServiceException("Email must be in a valid format");
        }

        return validEmail;
    }

    private static void requireId(int id, String entityName) throws AceOsServiceException {
        if (id <= 0) {
            throw new AceOsServiceException(entityName + " ID must be positive");
        }
    }

    private static <T> T requireValue(T value, String message) throws AceOsServiceException {
        if (value == null) {
            throw new AceOsServiceException(message);
        }

        return value;
    }

    private <T> T execute(
            UserRole requiredRole,
            String event,
            String failureMessage,
            ServiceOperation<T> operation)
            throws AceOsServiceException {
        try (Transactions tx = new Transactions()) {
            SecurityRepository.lockGate();
            User actor = SecurityRepository.validate(session, false);
            if (requiredRole != null) SecurityRepository.requireRole(actor, requiredRole);
            Scope.enter(actor);
            T result = operation.execute();
            Integer target =
                    switch (result) {
                        case User u -> u.getUserId();
                        case Asset a -> a.getAssetId();
                        case Complaint c -> c.getComplaintId();
                        case WorkOrder w -> w.getWorkOrderId();
                        case AssetInspection i -> i.getInspectionId();
                        case MaintenanceRecord m -> m.getMaintenanceRecordId();
                        case PreventiveMaintenanceSchedule p -> p.getMaintenanceScheduleId();
                        case null, default -> null;
                    };
            if (!event.startsWith("get"))
                SecurityRepository.audit(actor.getUserId(), event, target);
            tx.commit();
            return result;
        } catch (AceOsServiceException exception) {
            throw exception;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String message = exception.getMessage();

            throw new AceOsServiceException(
                    message == null || message.isBlank()
                            ? "The requested operation is not valid"
                            : message,
                    exception);
        } catch (SQLException exception) {
            throw translateDatabaseFailure(failureMessage, exception);
        } finally {
            Scope.clear();
        }
    }

    private static AceOsServiceException translateDatabaseFailure(
            String failureMessage, SQLException exception) {
        if (exception.getErrorCode() == 1062) {
            return new AceOsServiceException(
                    "A record with the same unique value " + "already exists", exception);
        }

        String sqlState = exception.getSQLState();

        if (sqlState != null && sqlState.startsWith("08")) {
            return new AceOsServiceException("AceOS could not connect to the database", exception);
        }

        if (sqlState != null && sqlState.startsWith("23")) {
            return new AceOsServiceException(
                    "The operation conflicts with existing " + "database records", exception);
        }

        return new AceOsServiceException(failureMessage, exception);
    }

    @FunctionalInterface
    private interface ServiceOperation<T> {

        T execute() throws SQLException, AceOsServiceException;
    }
}
