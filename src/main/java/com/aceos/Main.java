package com.aceos;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.model.MaintenanceRecord;
import com.aceos.model.User;
import com.aceos.model.UserRole;
import com.aceos.model.WorkOrder;

public class Main {

    public static void main(String[] args) {
        Asset asset = new Asset(
                1,
                "MUJ-AC-001",
                "Lecture Hall AC",
                "Air Conditioner",
                "AB1, Room 204",
                AssetStatus.ACTIVE);

        User requester = new User(
                1,
                "Aarav Sharma",
                "aarav@example.com",
                UserRole.REQUESTER);

        User technician = new User(
                2,
                "Meera Singh",
                "meera@example.com",
                UserRole.TECHNICIAN);

        User administrator = new User(
                3,
                "Rohan Verma",
                "rohan@example.com",
                UserRole.ADMINISTRATOR);

        Complaint complaint = new Complaint(
                1,
                asset,
                requester,
                "The air conditioner is running but not cooling the room",
                ComplaintPriority.HIGH);

        complaint.accept(administrator);

        WorkOrder workOrder = new WorkOrder(1, complaint, technician);
        workOrder.start();
        workOrder.complete();

        MaintenanceRecord maintenanceRecord = new MaintenanceRecord(
                1,
                workOrder,
                "Cleaned the filters and replaced the faulty capacitor");

        workOrder.verify(administrator);

        System.out.println("ACEOS WORKFLOW COMPLETED");
        System.out.println("Asset: " + asset.getAssetCode() + " - " + asset.getName());
        System.out.println("Reported by: " + complaint.getReportedBy().getName());
        System.out.println("Assigned technician: " + workOrder.getTechnician().getName());
        System.out.println("Maintenance action: " + maintenanceRecord.getActionTaken());
        System.out.println("Asset status: " + asset.getStatus());
        System.out.println("Complaint status: " + complaint.getStatus());
        System.out.println("Work order status: " + workOrder.getStatus());
    }
}
