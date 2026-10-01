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
import com.aceos.service.AceOsService;

import com.aceos.service.AceOsServiceException;

public class AceOsServiceTest {

    public static void main(String[] args)
        throws AceOsServiceException {
        AceOsService service =
                new AceOsService();

        long uniqueValue = System.currentTimeMillis();

        Asset asset = service.registerAsset(
                "SERVICE-TEST-" + uniqueValue,
                "Service Test Air Conditioner",
                "Air Conditioner",
                "Test Building, Room 606"
        );

        User requester = service.registerUser(
                "Service Test Requester",
                "service-requester-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.REQUESTER
        );

        User technician = service.registerUser(
                "Service Test Technician",
                "service-technician-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.TECHNICIAN
        );

        User administrator = service.registerUser(
                "Service Test Administrator",
                "service-administrator-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.ADMINISTRATOR
        );

        Complaint submittedComplaint =
                service.submitComplaint(
                        asset.getAssetId(),
                        requester.getUserId(),
                        "The air conditioner is not cooling",
                        ComplaintPriority.HIGH
                );

        require(
                service.getComplaint(
                        submittedComplaint.getComplaintId()
                ).getStatus() == ComplaintStatus.SUBMITTED,
                "The complaint was not persisted as submitted"
        );

        Complaint acceptedComplaint =
                service.acceptComplaint(
                        submittedComplaint.getComplaintId(),
                        administrator.getUserId()
                );

        require(
                acceptedComplaint.getStatus()
                        == ComplaintStatus.ACCEPTED,
                "The complaint was not accepted"
        );
        require(
                service.getComplaint(
                        submittedComplaint.getComplaintId()
                ).getStatus() == ComplaintStatus.ACCEPTED,
                "The accepted status was not persisted"
        );

        WorkOrder assignedWorkOrder =
                service.assignWorkOrder(
                        acceptedComplaint.getComplaintId(),
                        technician.getUserId()
                );

        require(
                assignedWorkOrder.getStatus()
                        == WorkOrderStatus.ASSIGNED,
                "The work order was not assigned"
        );
        require(
                service.getComplaint(
                        acceptedComplaint.getComplaintId()
                ).getStatus()
                        == ComplaintStatus.WORK_ORDER_CREATED,
                "The complaint was not linked to a work order"
        );

        WorkOrder inProgressWorkOrder =
                service.startWorkOrder(
                        assignedWorkOrder.getWorkOrderId()
                );

        require(
                inProgressWorkOrder.getStatus()
                        == WorkOrderStatus.IN_PROGRESS,
                "The work order was not started"
        );
        require(
                service.getAsset(asset.getAssetId()).getStatus()
                        == AssetStatus.UNDER_MAINTENANCE,
                "The asset was not placed under maintenance"
        );

        MaintenanceRecord savedMaintenanceRecord =
                service.completeWorkOrder(
                        assignedWorkOrder.getWorkOrderId(),
                        "Cleaned the filters and replaced "
                                + "the faulty capacitor"
                );

        require(
                savedMaintenanceRecord
                        .getMaintenanceRecordId() > 0,
                "The maintenance record was not saved"
        );
        require(
                savedMaintenanceRecord
                        .getWorkOrder()
                        .getStatus()
                        == WorkOrderStatus.COMPLETED,
                "The work order was not completed atomically"
        );

        WorkOrder completedWorkOrder =
                service.getWorkOrder(
                        assignedWorkOrder.getWorkOrderId()
                );

        require(
                completedWorkOrder.getStatus()
                        == WorkOrderStatus.COMPLETED,
                "The completed status was not persisted"
        );
        require(
                completedWorkOrder.getCompletedAt() != null,
                "The completion time was not persisted"
        );

        WorkOrder verifiedWorkOrder =
                service.verifyWorkOrder(
                        assignedWorkOrder.getWorkOrderId(),
                        administrator.getUserId()
                );

        MaintenanceRecord loadedMaintenanceRecord =
                service.getMaintenanceRecord(
                        savedMaintenanceRecord
                                .getMaintenanceRecordId()
                );

        Complaint finalComplaint =
                service.getComplaint(
                        acceptedComplaint.getComplaintId()
                );

        Asset finalAsset =
                service.getAsset(asset.getAssetId());

        require(
                verifiedWorkOrder.getStatus()
                        == WorkOrderStatus.VERIFIED,
                "The work order was not verified"
        );
        require(
                loadedMaintenanceRecord
                        .getWorkOrder()
                        .getStatus()
                        == WorkOrderStatus.VERIFIED,
                "The maintenance record did not reconstruct "
                        + "the verified work order"
        );
        require(
                finalComplaint.getStatus()
                        == ComplaintStatus.RESOLVED,
                "The complaint was not resolved"
        );
        require(
                finalAsset.getStatus()
                        == AssetStatus.ACTIVE,
                "The asset was not restored to active"
        );

        Complaint complaintToReject =
                service.submitComplaint(
                        asset.getAssetId(),
                        requester.getUserId(),
                        "A second complaint for rejection testing",
                        ComplaintPriority.LOW
                );

        service.rejectComplaint(
                complaintToReject.getComplaintId(),
                administrator.getUserId()
        );

        require(
                service.getComplaint(
                        complaintToReject.getComplaintId()
                ).getStatus() == ComplaintStatus.REJECTED,
                "The rejected status was not persisted"
        );

        System.out.println(
                "AceOS application-service verification passed"
        );
        System.out.println(
                "Asset ID: " + finalAsset.getAssetId()
        );
        System.out.println(
                "Accepted complaint ID: "
                        + finalComplaint.getComplaintId()
        );
        System.out.println(
                "Work-order ID: "
                        + verifiedWorkOrder.getWorkOrderId()
        );
        System.out.println(
                "Maintenance-record ID: "
                        + loadedMaintenanceRecord
                        .getMaintenanceRecordId()
        );
        System.out.println(
                "Final work-order status: "
                        + verifiedWorkOrder.getStatus()
        );
        System.out.println(
                "Final complaint status: "
                        + finalComplaint.getStatus()
        );
        System.out.println(
                "Final asset status: "
                        + finalAsset.getStatus()
        );
        System.out.println(
                "Rejected complaint status: "
                        + service.getComplaint(
                                complaintToReject.getComplaintId()
                        ).getStatus()
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