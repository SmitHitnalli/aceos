package com.aceos.service;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.MaintenanceRecordRepository;
import com.aceos.repository.MaintenanceWorkflowRepository;
import com.aceos.repository.UserRepository;
import com.aceos.repository.WorkOrderRepository;

import java.sql.SQLException;

public class AceOsService {

    private final UserRepository userRepository =
            new UserRepository();
    private final AssetRepository assetRepository =
            new AssetRepository();
    private final ComplaintRepository complaintRepository =
            new ComplaintRepository();
    private final WorkOrderRepository workOrderRepository =
            new WorkOrderRepository();
    private final MaintenanceRecordRepository
            maintenanceRecordRepository =
            new MaintenanceRecordRepository();
    private final MaintenanceWorkflowRepository
            maintenanceWorkflowRepository =
            new MaintenanceWorkflowRepository();

    public User registerUser(
            String name,
            String email,
            UserRole role) throws SQLException {
        return userRepository.save(
                new User(
                        name,
                        email,
                        role
                )
        );
    }

    public Asset registerAsset(
            String assetCode,
            String name,
            String category,
            String location) throws SQLException {
        return assetRepository.save(
                new Asset(
                        assetCode,
                        name,
                        category,
                        location,
                        AssetStatus.ACTIVE
                )
        );
    }

    public Complaint submitComplaint(
            int assetId,
            int requesterId,
            String description,
            ComplaintPriority priority)
            throws SQLException {
        Asset asset = requireAsset(assetId);
        User requester = requireUser(requesterId);

        if (requester.getRole() != UserRole.REQUESTER) {
            throw new IllegalArgumentException(
                    "Only a requester can submit a complaint"
            );
        }

        return complaintRepository.save(
                new Complaint(
                        asset,
                        requester,
                        description,
                        priority
                )
        );
    }

    public Complaint acceptComplaint(
            int complaintId,
            int administratorId) throws SQLException {
        Complaint complaint =
                requireComplaint(complaintId);
        User administrator =
                requireUser(administratorId);

        complaint.accept(administrator);
        complaintRepository.updateReview(complaint);

        return complaint;
    }

    public Complaint rejectComplaint(
            int complaintId,
            int administratorId) throws SQLException {
        Complaint complaint =
                requireComplaint(complaintId);
        User administrator =
                requireUser(administratorId);

        complaint.reject(administrator);
        complaintRepository.updateReview(complaint);

        return complaint;
    }

    public WorkOrder assignWorkOrder(
            int complaintId,
            int technicianId) throws SQLException {
        Complaint complaint =
                requireComplaint(complaintId);
        User technician =
                requireUser(technicianId);

        return workOrderRepository.save(
                new WorkOrder(
                        complaint,
                        technician
                )
        );
    }

    public WorkOrder startWorkOrder(
            int workOrderId) throws SQLException {
        WorkOrder workOrder =
                requireWorkOrder(workOrderId);

        workOrder.start();
        workOrderRepository.updateLifecycle(workOrder);

        return requireWorkOrder(workOrderId);
    }

    public MaintenanceRecord completeWorkOrder(
            int workOrderId,
            String actionTaken) throws SQLException {
        WorkOrder workOrder =
                requireWorkOrder(workOrderId);

        return maintenanceWorkflowRepository
                .completeWithMaintenanceRecord(
                        workOrder,
                        actionTaken
                );
    }

    public WorkOrder verifyWorkOrder(
            int workOrderId,
            int administratorId) throws SQLException {
        WorkOrder workOrder =
                requireWorkOrder(workOrderId);
        User administrator =
                requireUser(administratorId);

        workOrder.verify(administrator);
        workOrderRepository.updateLifecycle(workOrder);

        return requireWorkOrder(workOrderId);
    }

    public User getUser(
            int userId) throws SQLException {
        return requireUser(userId);
    }

    public Asset getAsset(
            int assetId) throws SQLException {
        return requireAsset(assetId);
    }

    public Complaint getComplaint(
            int complaintId) throws SQLException {
        return requireComplaint(complaintId);
    }

    public WorkOrder getWorkOrder(
            int workOrderId) throws SQLException {
        return requireWorkOrder(workOrderId);
    }

    public MaintenanceRecord getMaintenanceRecord(
            int maintenanceRecordId) throws SQLException {
        return maintenanceRecordRepository
                .findById(maintenanceRecordId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Maintenance record not found: "
                                + maintenanceRecordId
                ));
    }

    private User requireUser(
            int userId) throws SQLException {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId
                ));
    }

    private Asset requireAsset(
            int assetId) throws SQLException {
        return assetRepository
                .findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asset not found: " + assetId
                ));
    }

    private Complaint requireComplaint(
            int complaintId) throws SQLException {
        return complaintRepository
                .findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Complaint not found: " + complaintId
                ));
    }

    private WorkOrder requireWorkOrder(
            int workOrderId) throws SQLException {
        return workOrderRepository
                .findById(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Work order not found: " + workOrderId
                ));
    }
}