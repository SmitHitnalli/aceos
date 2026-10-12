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

public class WorkOrderRepositoryTest {

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
                        "WORK-ORDER-TEST-" + uniqueValue,
                        "Work-Order Test Air Conditioner",
                        "Air Conditioner",
                        "Test Building, Room 303",
                        AssetStatus.ACTIVE
                )
        );

        User savedRequester = userRepository.save(
                new User(
                        "Work-Order Test Requester",
                        "work-order-requester-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.REQUESTER
                )
        );

        User savedTechnician = userRepository.save(
                new User(
                        "Work-Order Test Technician",
                        "work-order-technician-"
                                + uniqueValue
                                + "@aceos.test",
                        UserRole.TECHNICIAN
                )
        );

        User administrator = new User(
                "Work-Order Test Administrator",
                "work-order-administrator-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.ADMINISTRATOR
        );

        Complaint complaint = new Complaint(
                savedAsset,
                savedRequester,
                "The air conditioner is making a loud noise",
                ComplaintPriority.HIGH
        );

        complaint.accept(administrator);

        Complaint savedComplaint =
                complaintRepository.save(complaint);

        WorkOrder unsavedWorkOrder = new WorkOrder(
                savedComplaint,
                savedTechnician
        );

        WorkOrder savedWorkOrder =
                workOrderRepository.save(unsavedWorkOrder);

        require(
                savedWorkOrder.getWorkOrderId() > 0,
                "MySQL did not generate a valid work-order ID"
        );

        WorkOrder loadedWorkOrder = workOrderRepository
                .findById(savedWorkOrder.getWorkOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "The saved work order could not be retrieved"
                ));

        Complaint reloadedComplaint = complaintRepository
                .findById(savedComplaint.getComplaintId())
                .orElseThrow(() -> new IllegalStateException(
                        "The work-order complaint could not be retrieved"
                ));

        require(
                loadedWorkOrder.getComplaint().getComplaintId()
                        == savedComplaint.getComplaintId(),
                "The retrieved complaint ID does not match"
        );

        require(
                loadedWorkOrder.getComplaint().getStatus()
                        == ComplaintStatus.WORK_ORDER_CREATED,
                "The joined complaint status does not match"
        );

        require(
                reloadedComplaint.getStatus()
                        == ComplaintStatus.WORK_ORDER_CREATED,
                "The complaint database status was not updated"
        );

        require(
                loadedWorkOrder.getTechnician().getUserId()
                        == savedTechnician.getUserId(),
                "The retrieved technician ID does not match"
        );

        require(
                loadedWorkOrder.getTechnician().getRole()
                        == UserRole.TECHNICIAN,
                "The retrieved technician role does not match"
        );

        require(
                loadedWorkOrder.getStatus()
                        == WorkOrderStatus.ASSIGNED,
                "The retrieved work-order status does not match"
        );

        require(
                loadedWorkOrder.getAssignedAt().equals(
                        savedWorkOrder.getAssignedAt()
                ),
                "The retrieved assignment time does not match"
        );

        require(
                loadedWorkOrder.getStartedAt() == null,
                "A new work order must not have a start time"
        );

        require(
                loadedWorkOrder.getCompletedAt() == null,
                "A new work order must not have a completion time"
        );

        require(
                loadedWorkOrder.getVerifiedAt() == null,
                "A new work order must not have a verification time"
        );

        System.out.println(
                "Work-order repository verification passed"
        );
        System.out.println(
                "Generated work-order ID: "
                        + loadedWorkOrder.getWorkOrderId()
        );
        System.out.println(
                "Loaded complaint ID: "
                        + loadedWorkOrder
                                .getComplaint()
                                .getComplaintId()
        );
        System.out.println(
                "Loaded complaint status: "
                        + loadedWorkOrder
                                .getComplaint()
                                .getStatus()
        );
        System.out.println(
                "Loaded technician: "
                        + loadedWorkOrder
                                .getTechnician()
                                .getEmail()
        );
        System.out.println(
                "Loaded work-order status: "
                        + loadedWorkOrder.getStatus()
        );
        System.out.println(
                "Loaded assignment time: "
                        + loadedWorkOrder.getAssignedAt()
        );
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}