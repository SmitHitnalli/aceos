package com.aceos.model;

public class DashboardStatistics {

    private final int totalAssets;
    private final int activeAssets;
    private final int openComplaints;
    private final int activeWorkOrders;
    private final int overdueSchedules;
    private final int failedInspections;

    public DashboardStatistics(
            int totalAssets,
            int activeAssets,
            int openComplaints,
            int activeWorkOrders,
            int overdueSchedules,
            int failedInspections) {
        this.totalAssets = totalAssets;
        this.activeAssets = activeAssets;
        this.openComplaints = openComplaints;
        this.activeWorkOrders = activeWorkOrders;
        this.overdueSchedules = overdueSchedules;
        this.failedInspections = failedInspections;
    }

    public int getTotalAssets() {
        return totalAssets;
    }

    public int getActiveAssets() {
        return activeAssets;
    }

    public int getOpenComplaints() {
        return openComplaints;
    }

    public int getActiveWorkOrders() {
        return activeWorkOrders;
    }

    public int getOverdueSchedules() {
        return overdueSchedules;
    }

    public int getFailedInspections() {
        return failedInspections;
    }
}