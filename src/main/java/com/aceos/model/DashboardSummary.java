package com.aceos.model;

import java.util.List;
import java.util.Map;

/** A bounded, role-scoped snapshot; no unrelated identities or records. */
public record DashboardSummary(Map<String, Integer> counts, List<Item> items) {
    public DashboardSummary {
        counts = Map.copyOf(counts);
        items = List.copyOf(items);
    }

    public record Item(String kind, int id, String asset, String status, String detail) {}
}
