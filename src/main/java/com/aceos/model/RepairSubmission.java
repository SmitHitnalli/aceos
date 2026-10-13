package com.aceos.model;

import java.time.LocalDateTime;

/** Immutable authorized submission and its optional terminal administrator review. */
public record RepairSubmission(int recordId, int number, String actionTaken, LocalDateTime at,
        Integer authorId, String authorName, String authorRole, String disposition,
        int reviewId, String reviewKind, Integer reviewerId, String reviewerName,
        String reviewerRole, String reason, LocalDateTime reviewedAt) {}
