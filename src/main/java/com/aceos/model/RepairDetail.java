package com.aceos.model;

import java.time.LocalDateTime;
import java.util.List;

/** One authorized snapshot of a complaint and its linked repair. */
public record RepairDetail(Complaint complaint, WorkOrder work, String actionTaken, List<Event> timeline, List<ComplaintNote> notes) {
    public RepairDetail { timeline = List.copyOf(timeline); notes = List.copyOf(notes); }
    public RepairDetail(Complaint complaint, WorkOrder work, String actionTaken, List<Event> timeline) {
        this(complaint,work,actionTaken,timeline,List.of());
    }
    public record Event(String description, LocalDateTime at) {}
}
