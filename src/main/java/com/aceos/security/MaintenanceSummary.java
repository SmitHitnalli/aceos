package com.aceos.security;

import java.time.LocalDateTime;

/** Technical history projection, excluding unrelated requester identity/contact. */
public record MaintenanceSummary(
        int recordId, int workOrderId, String actionTaken, LocalDateTime recordedAt,
        int submissionNumber,String disposition) {
    public MaintenanceSummary(int recordId,int workOrderId,String actionTaken,LocalDateTime recordedAt) {
        this(recordId,workOrderId,actionTaken,recordedAt,1,"Historical submission");
    }
}
