package com.aceos.model;

import com.aceos.security.MaintenanceSummary;
import java.time.LocalDateTime;
import java.util.List;

/** Authorized display projections; no unrelated requester identities are reconstructed. */
public record AssetProfile(Asset asset, UserRole role, List<MaintenanceSummary> maintenance,
        List<SafetyHold> holds, List<LifecycleEvent> lifecycle, List<ClearanceEvidence> evidence) {
    public record LifecycleEvent(ManualLifecycle before, ManualLifecycle after, String reason,
            int actorId, LocalDateTime at) {}
    public record ClearanceEvidence(int holdId, int id, boolean inspection, String description,
            LocalDateTime at) {
        public String label() {
            return (inspection ? "Passing inspection #" : "Verified repair #") + id + " · " + at + " · " + description;
        }
    }
}
