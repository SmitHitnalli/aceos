package com.aceos.model;

public enum ComplaintStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    WORK_ORDER_CREATED,
    RESOLVED,
    WITHDRAWN;

    public boolean isClosed() { return this == REJECTED || this == RESOLVED || this == WITHDRAWN; }
}
