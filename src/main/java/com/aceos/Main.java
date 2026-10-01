package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;
import com.aceos.service.AceOsService;

import com.aceos.service.AceOsServiceException;

public class Main {

    public static void main(String[] args) {
        try {
            runDatabaseWorkflow();
        } catch (AceOsServiceException | RuntimeException exception) {
            System.err.println(
                    "AceOS workflow failed: "
                            + exception.getMessage()
            );

            throw new IllegalStateException(
                    "The database-backed workflow could not finish",
                    exception
            );
        }
    }

    private static void runDatabaseWorkflow()
            throws AceOsServiceException {
        AceOsService service =
                new AceOsService();

        long uniqueValue = System.currentTimeMillis();

        Asset asset = service.registerAsset(
                "ACEOS-DEMO-" + uniqueValue,
                "Lecture Hall Air Conditioner",
                "Air Conditioner",
                "Academic Block, Room 204"
        );

        User requester = service.registerUser(
                "Aarav Sharma",
                "aarav-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.REQUESTER
        );

        User technician = service.registerUser(
                "Meera Singh",
                "meera-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.TECHNICIAN
        );

        User administrator = service.registerUser(
                "Rohan Verma",
                "rohan-"
                        + uniqueValue
                        + "@aceos.test",
                UserRole.ADMINISTRATOR
        );

        Complaint complaint = service.submitComplaint(
                asset.getAssetId(),
                requester.getUserId(),
                "The air conditioner is running "
                        + "but not cooling the room",
                ComplaintPriority.HIGH
        );

        Complaint acceptedComplaint =
                service.acceptComplaint(
                        complaint.getComplaintId(),
                        administrator.getUserId()
                );

        WorkOrder workOrder =
                service.assignWorkOrder(
                        acceptedComplaint.getComplaintId(),
                        technician.getUserId()
                );

        service.startWorkOrder(
                workOrder.getWorkOrderId()
        );

        MaintenanceRecord maintenanceRecord =
                service.completeWorkOrder(
                        workOrder.getWorkOrderId(),
                        "Cleaned the filters and replaced "
                                + "the faulty capacitor"
                );

        WorkOrder verifiedWorkOrder =
                service.verifyWorkOrder(
                        workOrder.getWorkOrderId(),
                        administrator.getUserId()
                );

        Complaint finalComplaint =
                service.getComplaint(
                        complaint.getComplaintId()
                );

        Asset finalAsset =
                service.getAsset(asset.getAssetId());

        MaintenanceRecord finalMaintenanceRecord =
                service.getMaintenanceRecord(
                        maintenanceRecord
                                .getMaintenanceRecordId()
                );

        System.out.println(
                "ACEOS DATABASE WORKFLOW COMPLETED"
        );
        System.out.println(
                "Asset: "
                        + finalAsset.getAssetCode()
                        + " - "
                        + finalAsset.getName()
        );
        System.out.println(
                "Reported by: " + requester.getName()
        );
        System.out.println(
                "Assigned technician: "
                        + verifiedWorkOrder
                        .getTechnician()
                        .getName()
        );
        System.out.println(
                "Maintenance action: "
                        + finalMaintenanceRecord
                        .getActionTaken()
        );
        System.out.println(
                "Asset status: "
                        + finalAsset.getStatus()
        );
        System.out.println(
                "Complaint status: "
                        + finalComplaint.getStatus()
        );
        System.out.println(
                "Work-order status: "
                        + verifiedWorkOrder.getStatus()
        );
    }
}
