package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.ComplaintStatus;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;
import com.aceos.repository.AssetRepository;
import com.aceos.repository.ComplaintRepository;
import com.aceos.repository.MaintenanceRecordRepository;
import com.aceos.repository.MaintenanceWorkflowRepository;
import com.aceos.repository.UserRepository;
import com.aceos.repository.WorkOrderRepository;

import java.sql.SQLException;

public class MaintenanceRecordRepositoryTest {

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
        MaintenanceRecordRepository
                maintenanceRecordRepository =
                new MaintenanceRecordRepository();
        MaintenanceWorkflowRepository
                maintenanceWorkflowRepository =
                new MaintenanceWorkflowRepository();

        long uniqueValue = System.currentTimeMillis();

        Asset savedAsset = assetRepository.save(
                new Asset(
                        "MAINTENANCE-TEST-" + uniqueValue,
                        "Maintenance Test Air Conditioner",
                        "Air Conditioner",
                        "Test Building, Room 505",
                        AssetStatus.ACTIVE
                )
        );

        User savedRequester = userRepository.save(
                new User(
                        "Maintenance Test Requester",
                        "maintenance-requester-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.REQUESTER
                )
        );

        User savedTechnician = userRepository.save(
                new User(
                        "Maintenance Test Technician",
                        "maintenance-technician-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.TECHNICIAN
                )
        );

        User administrator = new User(
                "Maintenance Test Administrator",
                "maintenance-administrator-"
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

        WorkOrder savedWorkOrder =
                workOrderRepository.save(
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

        MaintenanceRecord savedMaintenanceRecord =
                maintenanceWorkflowRepository
                        .completeWithMaintenanceRecord(
                                inProgressWorkOrder,
                                "Cleaned the filters and replaced "
                                        + "the faulty capacitor"
                        );

        WorkOrder completedWorkOrder = loadWorkOrder(
                workOrderRepository,
                savedWorkOrder.getWorkOrderId()
        );

        require(
                completedWorkOrder.getStatus()
                        == WorkOrderStatus.COMPLETED,
                "The work order and maintenance record "
                        + "were not committed together"
        );
        require(
                completedWorkOrder.getCompletedAt() != null,
                "The completion time was not persisted"
        );

        MaintenanceRecord loadedMaintenanceRecord =
                loadMaintenanceRecord(
                        maintenanceRecordRepository,
                        savedMaintenanceRecord
                                .getMaintenanceRecordId()
                );

        require(
                savedMaintenanceRecord
                        .getMaintenanceRecordId() > 0,
                "MySQL did not generate a maintenance-record ID"
        );
        require(
                loadedMaintenanceRecord
                        .getMaintenanceRecordId()
                        == savedMaintenanceRecord
                        .getMaintenanceRecordId(),
                "The maintenance-record ID was not reconstructed"
        );
        require(
                loadedMaintenanceRecord
                        .getWorkOrder()
                        .getWorkOrderId()
                        == savedWorkOrder.getWorkOrderId(),
                "The related work order was not reconstructed"
        );
        require(
                loadedMaintenanceRecord
                        .getWorkOrder()
                        .getStatus()
                        == WorkOrderStatus.COMPLETED,
                "The completed work-order status was not loaded"
        );
        require(
                loadedMaintenanceRecord
                        .getActionTaken()
                        .equals(
                                savedMaintenanceRecord
                                        .getActionTaken()
                        ),
                "The maintenance action was not reconstructed"
        );
        require(
                loadedMaintenanceRecord
                        .getRecordedAt()
                        .equals(
                                savedMaintenanceRecord
                                        .getRecordedAt()
                        ),
                "The maintenance record time was not reconstructed"
        );

        completedWorkOrder.verify(administrator);
        workOrderRepository.updateLifecycle(
                completedWorkOrder
        );

        MaintenanceRecord verifiedMaintenanceRecord =
                loadMaintenanceRecord(
                        maintenanceRecordRepository,
                        savedMaintenanceRecord
                                .getMaintenanceRecordId()
                );

        require(
                verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getStatus()
                        == WorkOrderStatus.VERIFIED,
                "The verified work order was not reconstructed"
        );
        require(
                verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getComplaint()
                        .getStatus()
                        == ComplaintStatus.RESOLVED,
                "The resolved complaint was not reconstructed"
        );
        require(
                verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getComplaint()
                        .getAsset()
                        .getStatus()
                        == AssetStatus.ACTIVE,
                "The active asset was not reconstructed"
        );

        System.out.println(
                "Maintenance-record repository verification passed"
        );
        System.out.println(
                "Generated maintenance-record ID: "
                        + verifiedMaintenanceRecord
                        .getMaintenanceRecordId()
        );
        System.out.println(
                "Loaded work-order ID: "
                        + verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getWorkOrderId()
        );
        System.out.println(
                "Loaded work-order status: "
                        + verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getStatus()
        );
        System.out.println(
                "Loaded action: "
                        + verifiedMaintenanceRecord
                        .getActionTaken()
        );
        System.out.println(
                "Recorded at: "
                        + verifiedMaintenanceRecord
                        .getRecordedAt()
        );
        System.out.println(
                "Final complaint status: "
                        + verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getComplaint()
                        .getStatus()
        );
        System.out.println(
                "Final asset status: "
                        + verifiedMaintenanceRecord
                        .getWorkOrder()
                        .getComplaint()
                        .getAsset()
                        .getStatus()
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

    private static MaintenanceRecord loadMaintenanceRecord(
            MaintenanceRecordRepository repository,
            int maintenanceRecordId) throws SQLException {
        return repository
                .findById(maintenanceRecordId)
                .orElseThrow(() -> new IllegalStateException(
                        "The maintenance record could not be retrieved"
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
