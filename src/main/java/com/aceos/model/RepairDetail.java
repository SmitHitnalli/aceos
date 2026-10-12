package com.aceos.model;

import java.time.LocalDateTime;
import java.util.List;

/** One authorized snapshot of a complaint and its linked repair. */
public record RepairDetail(Complaint complaint, WorkOrder work, String actionTaken, List<Event> timeline) {
    public RepairDetail { timeline = List.copyOf(timeline); }
    public record Event(String description, LocalDateTime at) {}
}
