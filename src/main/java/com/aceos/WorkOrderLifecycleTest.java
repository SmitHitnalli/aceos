package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.UserRepository;
import com.aceos.repository.WorkOrderRepository;

import java.sql.SQLException;

public class WorkOrderLifecycleTest {

    public static void main(String[] args) throws SQLException {
        com.aceos.security.LegacyVerificationGuard.retired();
        AssetRepository assetRepository =
                new AssetRepository();
        UserRepository userRepository =
                new UserRepository();
        ComplaintRepository complaintRepository =
                new ComplaintRepository();
        WorkOrderRepository workOrderRepository =
                new WorkOrderRepository();

        long uniqueValue = System.currentTimeMillis();

        Asset savedAsset = assetRepository.save(
                new Asset(
                        "LIFECYCLE-TEST-" + uniqueValue,
                        "Lifecycle Test Air Conditioner",
                        "Air Conditioner",
                        "Test Building, Room 404",
                        AssetStatus.ACTIVE
                )
        );

        User savedRequester = userRepository.save(
                new User(
                        "Lifecycle Test Requester",
                        "lifecycle-requester-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.REQUESTER
                )
        );

        User savedTechnician = userRepository.save(
                new User(
                        "Lifecycle Test Technician",
                        "lifecycle-technician-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.TECHNICIAN
                )
        );

        User administrator = new User(
                "Lifecycle Test Administrator",
                "lifecycle-administrator-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.ADMINISTRATOR
        );

        Complaint complaint = new Complaint(
                savedAsset,
                savedRequester,
                "The air conditioner requires maintenance",
                ComplaintPriority.HIGH
        );

        complaint.accept(administrator);

        Complaint savedComplaint =
                complaintRepository.save(complaint);

        WorkOrder savedWorkOrder = workOrderRepository.save(
                new WorkOrder(
                        savedComplaint,
                        savedTechnician
                )
        );

        WorkOrder assignedWorkOrder = loadWorkOrder(
                workOrderRepository,
                savedWorkOrder.getWorkOrderId()
        );

        assignedWorkOrder.start();
        workOrderRepository.updateLifecycle(
                assignedWorkOrder
        );

        WorkOrder inProgressWorkOrder = loadWorkOrder(
                workOrderRepository,
                savedWorkOrder.getWorkOrderId()
        );

        require(
                inProgressWorkOrder.getStatus()
                        == WorkOrderStatus.IN_PROGRESS,
                "The work order was not persisted as in progress"
        );
        require(
                inProgressWorkOrder.getStartedAt() != null,
                "The start time was not persisted"
        );
        require(
                inProgressWorkOrder
                        .getComplaint()
                        .getAsset()
                        .getStatus()
                        == AssetStatus.UNDER_MAINTENANCE,
                "The asset was not persisted as under maintenance"
        );

        inProgressWorkOrder.complete();
        workOrderRepository.updateLifecycle(
                inProgressWorkOrder
        );

        WorkOrder completedWorkOrder = loadWorkOrder(
                workOrderRepository,
                savedWorkOrder.getWorkOrderId()
        );

        require(
                completedWorkOrder.getStatus()
                        == WorkOrderStatus.COMPLETED,
                "The work order was not persisted as completed"
        );
        require(
                completedWorkOrder.getCompletedAt() != null,
                "The completion time was not persisted"
        );
        require(
                completedWorkOrder
                        .getComplaint()
                        .getAsset()
                        .getStatus()
                        == AssetStatus.UNDER_MAINTENANCE,
                "The asset should remain under maintenance"
        );

        completedWorkOrder.verify(administrator);
        workOrderRepository.updateLifecycle(
                completedWorkOrder
        );

        WorkOrder verifiedWorkOrder = loadWorkOrder(
                workOrderRepository,
                savedWorkOrder.getWorkOrderId()
        );

        require(
                verifiedWorkOrder.getStatus()
                        == WorkOrderStatus.VERIFIED,
                "The work order was not persisted as verified"
        );
        require(
                verifiedWorkOrder.getVerifiedAt() != null,
                "The verification time was not persisted"
        );
        require(
                verifiedWorkOrder
                        .getComplaint()
                        .getStatus()
                        == ComplaintStatus.RESOLVED,
                "The complaint was not persisted as resolved"
        );
        require(
                verifiedWorkOrder
                        .getComplaint()
                        .getAsset()
                        .getStatus()
                        == AssetStatus.ACTIVE,
                "The asset was not restored to active"
        );

        Complaint finalComplaint = complaintRepository
                .findById(savedComplaint.getComplaintId())
                .orElseThrow(() -> new IllegalStateException(
                        "The final complaint could not be retrieved"
                ));

        Asset finalAsset = assetRepository
                .findById(savedAsset.getAssetId())
                .orElseThrow(() -> new IllegalStateException(
                        "The final asset could not be retrieved"
                ));

        require(
                finalComplaint.getStatus()
                        == ComplaintStatus.RESOLVED,
                "The complaint table was not updated"
        );
        require(
                finalAsset.getStatus()
                        == AssetStatus.ACTIVE,
                "The asset table was not updated"
        );

        System.out.println(
                "Work-order lifecycle verification passed"
        );
        System.out.println(
                "Work-order ID: "
                        + verifiedWorkOrder.getWorkOrderId()
        );
        System.out.println(
                "Final work-order status: "
                        + verifiedWorkOrder.getStatus()
        );
        System.out.println(
                "Started at: "
                        + verifiedWorkOrder.getStartedAt()
        );
        System.out.println(
                "Completed at: "
                        + verifiedWorkOrder.getCompletedAt()
        );
        System.out.println(
                "Verified at: "
                        + verifiedWorkOrder.getVerifiedAt()
        );
        System.out.println(
                "Final complaint status: "
                        + finalComplaint.getStatus()
        );
        System.out.println(
                "Final asset status: "
                        + finalAsset.getStatus()
        );
    }

    private static WorkOrder loadWorkOrder(
            WorkOrderRepository repository,
            int workOrderId) throws SQLException {
        return repository
                .findById(workOrderId)
                .orElseThrow(() -> new IllegalStateException(
                        "The work order could not be retrieved"
                ));
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}