package com.aceos.model;

/** Administrator assignment projection; overdue inspections are included in scheduled inspections. */
public record TechnicianWorkload(User technician, int awaitingStart, int inProgress,
        int awaitingVerification, int scheduledInspections, int overdueInspections) {}
