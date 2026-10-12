package com.aceos.security;

import java.time.LocalDateTime;

/** Technical history projection, excluding unrelated requester identity/contact. */
public record MaintenanceSummary(
        int recordId, int workOrderId, String actionTaken, LocalDateTime recordedAt) {}
