package com.aceos.security;

import com.aceos.model.User;
import com.aceos.model.UserRole;

/** SQL read scope: applied before repositories reconstruct any object graph. */
public final class Scope {
    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private Scope() {}

    public static User user() {
        return CURRENT.get();
    }

    public static void enter(User user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String filter(String sql, String kind, String alias) {
        User user = CURRENT.get();
        if (user == null || user.getRole() == UserRole.ADMINISTRATOR) return sql;
        int id = user.getUserId();
        boolean requester = user.getRole() == UserRole.REQUESTER;
        String assetId = alias.isEmpty() ? "asset_id" : alias + ".asset_id";
        String assetScope =
                "("
                        + assetId
                        + " IN (SELECT c.asset_id FROM complaints c JOIN work_orders w ON"
                        + " w.complaint_id=c.complaint_id WHERE w.technician_user_id="
                        + id
                        + ") OR "
                        + assetId
                        + " IN (SELECT asset_id FROM asset_inspections WHERE assigned_to_user_id="
                        + id
                        + "))";
        String condition =
                switch (kind) {
                    case "assets" -> requester ? "status<>'RETIRED'" : assetScope;
                    case "complaints" ->
                            requester
                                    ? "c.reported_by_user_id=" + id
                                    : "c.complaint_id IN (SELECT complaint_id FROM work_orders"
                                          + " WHERE technician_user_id="
                                            + id
                                            + ")";
                    case "work_orders" ->
                            requester
                                    ? "c.reported_by_user_id=" + id
                                    : "w.technician_user_id=" + id;
                    case "inspections" -> requester ? "1=0" : "i.assigned_to_user_id=" + id;
                    case "schedules" -> requester ? "1=0" : assetScope;
                    case "records" ->
                            "work_order_id IN (SELECT w.work_order_id FROM work_orders w JOIN"
                                + " complaints c ON c.complaint_id=w.complaint_id WHERE "
                                    + (requester
                                            ? "c.reported_by_user_id="
                                            : "w.technician_user_id=")
                                    + id
                                    + ")";
                    default -> throw new IllegalArgumentException("Unknown read scope");
                };
        int order = sql.toUpperCase(java.util.Locale.ROOT).indexOf("ORDER BY");
        String head = order < 0 ? sql : sql.substring(0, order),
                tail = order < 0 ? "" : sql.substring(order);
        return head
                + (head.toUpperCase(java.util.Locale.ROOT).contains("WHERE") ? " AND " : " WHERE ")
                + condition
                + " "
                + tail;
    }
}
