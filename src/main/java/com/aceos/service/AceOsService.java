package com.aceos.service;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.MaintenanceRecordRepository;
import com.aceos.repository.MaintenanceWorkflowRepository;
import com.aceos.repository.UserRepository;
import com.aceos.repository.WorkOrderRepository;
import com.aceos.repository.PreventiveMaintenanceScheduleRepository;

import java.sql.SQLException;
import java.util.List;
import java.time.LocalDate;

public class AceOsService {

    private final UserRepository userRepository
            = new UserRepository();
    private final AssetRepository assetRepository
            = new AssetRepository();
    private final ComplaintRepository complaintRepository
            = new ComplaintRepository();
    private final WorkOrderRepository workOrderRepository
            = new WorkOrderRepository();
    private final MaintenanceRecordRepository maintenanceRecordRepository
            = new MaintenanceRecordRepository();
    private final MaintenanceWorkflowRepository maintenanceWorkflowRepository
            = new MaintenanceWorkflowRepository();
    private final PreventiveMaintenanceScheduleRepository preventiveMaintenanceScheduleRepository
            = new PreventiveMaintenanceScheduleRepository();

    public User registerUser(
            String name,
            String email,
            UserRole role) throws AceOsServiceException {
        String validName = requireText(
                name,
                "User name",
                100
        );
        String validEmail = requireEmail(email);
        UserRole validRole = requireValue(
                role,
                "User role is required"
        );

        return execute(
                "The user could not be registered",
                () -> userRepository.save(
                        new User(
                                validName,
                                validEmail,
                                validRole
                        )
                )
        );
    }

    public Asset registerAsset(
            String assetCode,
            String name,
            String category,
            String location) throws AceOsServiceException {
        String validAssetCode = requireText(
                assetCode,
                "Asset code",
                50
        );
        String validName = requireText(
                name,
                "Asset name",
                100
        );
        String validCategory = requireText(
                category,
                "Asset category",
                100
        );
        String validLocation = requireText(
                location,
                "Asset location",
                150
        );

        return execute(
                "The asset could not be registered",
                () -> assetRepository.save(
                        new Asset(
                                validAssetCode,
                                validName,
                                validCategory,
                                validLocation,
                                AssetStatus.ACTIVE
                        )
                )
        );
    }

    public PreventiveMaintenanceSchedule
            createPreventiveMaintenanceSchedule(
                    int assetId,
                    String taskDescription,
                    int frequencyDays,
                    LocalDate nextDueDate)
            throws AceOsServiceException {

        requireId(assetId, "Asset");

        String validTaskDescription = requireText(
                taskDescription,
                "Maintenance task",
                255
        );

        if (frequencyDays <= 0) {
            throw new AceOsServiceException(
                    "Frequency must be greater than zero"
            );
        }

        LocalDate validNextDueDate = requireValue(
                nextDueDate,
                "Next due date is required"
        );

        if (validNextDueDate.isBefore(LocalDate.now())) {
            throw new AceOsServiceException(
                    "Next due date cannot be in the past"
            );
        }

        return execute(
                "The preventive maintenance schedule "
                + "could not be created",
                () -> {
                    Asset asset = requireAsset(assetId);

                    if (asset.getStatus() == AssetStatus.RETIRED) {
                        throw new IllegalStateException(
                                "A retired asset cannot receive "
                                + "a maintenance schedule"
                        );
                    }

                    return preventiveMaintenanceScheduleRepository
                            .save(
                                    new PreventiveMaintenanceSchedule(
                                            asset,
                                            validTaskDescription,
                                            frequencyDays,
                                            validNextDueDate
                                    )
                            );
                }
        );
    }

    public Complaint submitComplaint(
            int assetId,
            int requesterId,
            String description,
            ComplaintPriority priority)
            throws AceOsServiceException {
        requireId(assetId, "Asset");
        requireId(requesterId, "Requester");

        String validDescription = requireText(
                description,
                "Complaint description",
                5000
        );
        ComplaintPriority validPriority = requireValue(
                priority,
                "Complaint priority is required"
        );

        return execute(
                "The complaint could not be submitted",
                () -> {
                    Asset asset = requireAsset(assetId);
                    User requester = requireUser(requesterId);

                    if (requester.getRole()
                    != UserRole.REQUESTER) {
                        throw new IllegalArgumentException(
                                "Only a requester can submit "
                                + "a complaint"
                        );
                    }

                    return complaintRepository.save(
                            new Complaint(
                                    asset,
                                    requester,
                                    validDescription,
                                    validPriority
                            )
                    );
                }
        );
    }

    public Complaint acceptComplaint(
            int complaintId,
            int administratorId)
            throws AceOsServiceException {
        requireId(complaintId, "Complaint");
        requireId(administratorId, "Administrator");

        return execute(
                "The complaint could not be accepted",
                () -> {
                    Complaint complaint
                    = requireComplaint(complaintId);
                    User administrator
                    = requireUser(administratorId);

                    complaint.accept(administrator);
                    complaintRepository.updateReview(complaint);

                    return complaint;
                }
        );
    }

    public Complaint rejectComplaint(
            int complaintId,
            int administratorId)
            throws AceOsServiceException {
        requireId(complaintId, "Complaint");
        requireId(administratorId, "Administrator");

        return execute(
                "The complaint could not be rejected",
                () -> {
                    Complaint complaint
                    = requireComplaint(complaintId);
                    User administrator
                    = requireUser(administratorId);

                    complaint.reject(administrator);
                    complaintRepository.updateReview(complaint);

                    return complaint;
                }
        );
    }

    public WorkOrder assignWorkOrder(
            int complaintId,
            int technicianId)
            throws AceOsServiceException {
        requireId(complaintId, "Complaint");
        requireId(technicianId, "Technician");

        return execute(
                "The work order could not be assigned",
                () -> {
                    Complaint complaint
                    = requireComplaint(complaintId);
                    User technician
                    = requireUser(technicianId);

                    return workOrderRepository.save(
                            new WorkOrder(
                                    complaint,
                                    technician
                            )
                    );
                }
        );
    }

    public WorkOrder startWorkOrder(
            int workOrderId)
            throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        return execute(
                "The work order could not be started",
                () -> {
                    WorkOrder workOrder
                    = requireWorkOrder(workOrderId);

                    workOrder.start();
                    workOrderRepository.updateLifecycle(
                            workOrder
                    );

                    return requireWorkOrder(workOrderId);
                }
        );
    }

    public MaintenanceRecord completeWorkOrder(
            int workOrderId,
            String actionTaken)
            throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        String validAction = requireText(
                actionTaken,
                "Maintenance action",
                5000
        );

        return execute(
                "The work order could not be completed",
                () -> {
                    WorkOrder workOrder
                    = requireWorkOrder(workOrderId);

                    return maintenanceWorkflowRepository
                            .completeWithMaintenanceRecord(
                                    workOrder,
                                    validAction
                            );
                }
        );
    }

    public WorkOrder verifyWorkOrder(
            int workOrderId,
            int administratorId)
            throws AceOsServiceException {
        requireId(workOrderId, "Work order");
        requireId(administratorId, "Administrator");

        return execute(
                "The work order could not be verified",
                () -> {
                    WorkOrder workOrder
                    = requireWorkOrder(workOrderId);
                    User administrator
                    = requireUser(administratorId);

                    workOrder.verify(administrator);
                    workOrderRepository.updateLifecycle(
                            workOrder
                    );

                    return requireWorkOrder(workOrderId);
                }
        );
    }

    public User getUser(
            int userId) throws AceOsServiceException {
        requireId(userId, "User");

        return execute(
                "The user could not be loaded",
                () -> requireUser(userId)
        );
    }

    public List<User> getAllUsers()
            throws AceOsServiceException {
        return execute(
                "The users could not be loaded",
                userRepository::findAll
        );
    }

    public Asset getAsset(
            int assetId) throws AceOsServiceException {
        requireId(assetId, "Asset");

        return execute(
                "The asset could not be loaded",
                () -> requireAsset(assetId)
        );
    }

    public List<Asset> getAllAssets()
            throws AceOsServiceException {
        return execute(
                "The assets could not be loaded",
                assetRepository::findAll
        );
    }

    public Complaint getComplaint(
            int complaintId) throws AceOsServiceException {
        requireId(complaintId, "Complaint");

        return execute(
                "The complaint could not be loaded",
                () -> requireComplaint(complaintId)
        );
    }

    public List<Complaint> getAllComplaints()
            throws AceOsServiceException {
        return execute(
                "The complaints could not be loaded",
                complaintRepository::findAll
        );
    }

    public WorkOrder getWorkOrder(
            int workOrderId) throws AceOsServiceException {
        requireId(workOrderId, "Work order");

        return execute(
                "The work order could not be loaded",
                () -> requireWorkOrder(workOrderId)
        );
    }

    public List<WorkOrder> getAllWorkOrders()
            throws AceOsServiceException {
        return execute(
                "The work orders could not be loaded",
                workOrderRepository::findAll
        );
    }

    public MaintenanceRecord getMaintenanceRecord(
            int maintenanceRecordId)
            throws AceOsServiceException {
        requireId(
                maintenanceRecordId,
                "Maintenance record"
        );

        return execute(
                "The maintenance record could not be loaded",
                () -> maintenanceRecordRepository
                        .findById(maintenanceRecordId)
                        .orElseThrow(
                                () -> new AceOsServiceException(
                                        "Maintenance record not found: "
                                        + maintenanceRecordId
                                )
                        )
        );
    }

    private User requireUser(
            int userId)
            throws SQLException, AceOsServiceException {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new AceOsServiceException(
                "User not found: " + userId
        ));
    }

    private Asset requireAsset(
            int assetId)
            throws SQLException, AceOsServiceException {
        return assetRepository
                .findById(assetId)
                .orElseThrow(() -> new AceOsServiceException(
                "Asset not found: " + assetId
        ));
    }

    private Complaint requireComplaint(
            int complaintId)
            throws SQLException, AceOsServiceException {
        return complaintRepository
                .findById(complaintId)
                .orElseThrow(() -> new AceOsServiceException(
                "Complaint not found: " + complaintId
        ));
    }

    private WorkOrder requireWorkOrder(
            int workOrderId)
            throws SQLException, AceOsServiceException {
        return workOrderRepository
                .findById(workOrderId)
                .orElseThrow(() -> new AceOsServiceException(
                "Work order not found: " + workOrderId
        ));
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength)
            throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException(
                    fieldName + " is required"
            );
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new AceOsServiceException(
                    fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters"
            );
        }

        return normalizedValue;
    }

    private static String requireEmail(
            String email) throws AceOsServiceException {
        String validEmail = requireText(
                email,
                "Email",
                255
        );

        int atIndex = validEmail.indexOf('@');
        int finalDotIndex = validEmail.lastIndexOf('.');

        if (atIndex <= 0
                || atIndex != validEmail.lastIndexOf('@')
                || finalDotIndex <= atIndex + 1
                || finalDotIndex == validEmail.length() - 1
                || validEmail.chars().anyMatch(
                        Character::isWhitespace
                )) {
            throw new AceOsServiceException(
                    "Email must be in a valid format"
            );
        }

        return validEmail;
    }

    private static void requireId(
            int id,
            String entityName)
            throws AceOsServiceException {
        if (id <= 0) {
            throw new AceOsServiceException(
                    entityName + " ID must be positive"
            );
        }
    }

    private static <T> T requireValue(
            T value,
            String message)
            throws AceOsServiceException {
        if (value == null) {
            throw new AceOsServiceException(message);
        }

        return value;
    }

    private <T> T execute(
            String failureMessage,
            ServiceOperation<T> operation)
            throws AceOsServiceException {
        try {
            return operation.execute();
        } catch (AceOsServiceException exception) {
            throw exception;
        } catch (IllegalArgumentException
                | IllegalStateException exception) {
            String message = exception.getMessage();

            throw new AceOsServiceException(
                    message == null || message.isBlank()
                    ? "The requested operation is not valid"
                    : message,
                    exception
            );
        } catch (SQLException exception) {
            throw translateDatabaseFailure(
                    failureMessage,
                    exception
            );
        }
    }

    private static AceOsServiceException
            translateDatabaseFailure(
                    String failureMessage,
                    SQLException exception) {
        if (exception.getErrorCode() == 1062) {
            return new AceOsServiceException(
                    "A record with the same unique value "
                    + "already exists",
                    exception
            );
        }

        String sqlState = exception.getSQLState();

        if (sqlState != null && sqlState.startsWith("08")) {
            return new AceOsServiceException(
                    "AceOS could not connect to the database",
                    exception
            );
        }

        if (sqlState != null && sqlState.startsWith("23")) {
            return new AceOsServiceException(
                    "The operation conflicts with existing "
                    + "database records",
                    exception
            );
        }

        return new AceOsServiceException(
                failureMessage,
                exception
        );
    }

    @FunctionalInterface
    private interface ServiceOperation<T> {

        T execute()
                throws SQLException, AceOsServiceException;
    }
}
