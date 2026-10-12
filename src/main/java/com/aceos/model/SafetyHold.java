package com.aceos.model;

import java.time.LocalDateTime;

public record SafetyHold(int holdId, int assetId, int failedInspectionId,
        LocalDateTime createdAt, Integer clearedBy, String clearanceReason,
        Integer passingInspectionId, Integer verifiedWorkOrderId, LocalDateTime clearedAt) {
    @Override public String toString() {
        return "Hold #" + holdId + " · failed inspection #" + failedInspectionId
                + (clearedAt == null ? " · active" : " · cleared");
    }
}
