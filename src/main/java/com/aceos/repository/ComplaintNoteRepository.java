package com.aceos.repository;

import com.aceos.model.*;
import com.aceos.security.SecurityRepository;
import com.aceos.security.Scope;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Service authorization and the parent complaint SQL scope are required before use. */
public final class ComplaintNoteRepository {
    public void append(int complaintId, User author, ComplaintNote.Kind kind, String body) throws SQLException {
        try (var p = SecurityRepository.connection().prepareStatement("""
                INSERT INTO complaint_notes(complaint_id,author_user_id,author_role,kind,body)
                VALUES (?,?,?,?,?)
                """)) {
            p.setInt(1,complaintId); p.setInt(2,author.getUserId()); p.setString(3,author.getRole().name());
            p.setString(4,kind.name()); p.setString(5,body);
            if (p.executeUpdate()!=1) throw new SQLException("Expected one complaint note");
        }
    }

    public List<ComplaintNote> list(int complaintId) throws SQLException {
        var notes = new ArrayList<ComplaintNote>();
        String sql = Scope.filter("""
                SELECT n.note_id,n.author_user_id,u.name,n.author_role,n.kind,n.body,n.recorded_at
                FROM complaint_notes n JOIN complaints c ON c.complaint_id=n.complaint_id
                JOIN users u ON u.user_id=n.author_user_id WHERE c.complaint_id=?
                ORDER BY n.recorded_at,n.note_id
                """, "complaints", "c");
        try (var p=SecurityRepository.connection().prepareStatement(sql)) {
            p.setInt(1,complaintId);
            try(var r=p.executeQuery()) {
                while(r.next()) notes.add(new ComplaintNote(r.getLong(1),r.getInt(2),r.getString(3),
                        UserRole.valueOf(r.getString(4)),ComplaintNote.Kind.valueOf(r.getString(5)),
                        r.getString(6),r.getTimestamp(7).toLocalDateTime()));
            }
        }
        return List.copyOf(notes);
    }
}
