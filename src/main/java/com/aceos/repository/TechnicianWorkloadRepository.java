package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.SecurityRepository;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One aggregate read within the administrator service transaction. */
public final class TechnicianWorkloadRepository {
    public List<TechnicianWorkload> load(LocalDate today) throws SQLException {
        var result = new ArrayList<TechnicianWorkload>();
        try (var p = SecurityRepository.connection().prepareStatement("""
                SELECT u.user_id,u.name,u.email,
                    COALESCE(w.waiting,0) waiting,COALESCE(w.started,0) started,
                    COALESCE(w.verification,0) verification,
                    COALESCE(i.scheduled,0) scheduled,COALESCE(i.overdue,0) overdue
                FROM users u JOIN user_accounts a ON a.user_id=u.user_id
                LEFT JOIN (
                    SELECT technician_user_id,
                        SUM(status='ASSIGNED') waiting,SUM(status='IN_PROGRESS') started,
                        SUM(status='COMPLETED') verification
                    FROM work_orders GROUP BY technician_user_id
                ) w ON w.technician_user_id=u.user_id
                LEFT JOIN (
                    SELECT assigned_to_user_id,COUNT(*) scheduled,
                        SUM(scheduled_date<?) overdue
                    FROM asset_inspections WHERE status='SCHEDULED'
                    GROUP BY assigned_to_user_id
                ) i ON i.assigned_to_user_id=u.user_id
                WHERE u.role='TECHNICIAN' AND a.state='ACTIVE' AND a.must_change=FALSE
                ORDER BY u.name,u.user_id
                """)) {
            p.setDate(1, Date.valueOf(today));
            try (var r = p.executeQuery()) {
                while (r.next()) result.add(new TechnicianWorkload(
                        new User(r.getInt("user_id"),r.getString("name"),r.getString("email"),UserRole.TECHNICIAN),
                        r.getInt("waiting"),r.getInt("started"),r.getInt("verification"),
                        r.getInt("scheduled"),r.getInt("overdue")));
            }
        }
        return List.copyOf(result);
    }
}
