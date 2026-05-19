package com.project.artconnect.persistence;

import com.project.artconnect.model.Booking;
import com.project.artconnect.model.CommunityMember;
import com.project.artconnect.model.Workshop;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO JDBC pour les inscriptions (Booking) aux ateliers.
 * 
 * Implementation utilisee directement par JdbcWorkshopService (pas d'interface
 * dediee dans le squelette d'origine).
 * 
 * Utilise une transaction pour garantir l'atomicite de l'inscription
 * (le trigger SQL tr_check_workshop_availability se charge de verifier le
 * nombre maximum de participants cote BDD).
 */
public class JdbcBookingDao {

    private static final String SQL_INSERT =
            "INSERT INTO booking (workshop_id, member_id, booking_date, payment_status) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_FIND_BY_MEMBER =
            "SELECT b.id, b.booking_date, b.payment_status, " +
            "       w.id AS w_id, w.title AS w_title, w.date AS w_date, w.price AS w_price, " +
            "       w.duration_minutes AS w_duration, w.max_participants AS w_max, " +
            "       w.location AS w_loc, w.description AS w_desc, w.level AS w_level, " +
            "       w.instructor_id AS w_instructor, " +
            "       a.name AS a_name, a.bio AS a_bio, a.birth_year AS a_birth, " +
            "       a.contact_email AS a_email, a.city AS a_city " +
            "FROM booking b " +
            "JOIN workshop w ON w.id = b.workshop_id " +
            "JOIN artist a ON a.id = w.instructor_id " +
            "WHERE b.member_id = ? ORDER BY w.date";

    private static final String SQL_WORKSHOP_ID_BY_TITLE = "SELECT id FROM workshop WHERE title = ?";
    private static final String SQL_MEMBER_ID_BY_NAME = "SELECT id FROM community_member WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    /**
     * Inscrit un membre a un atelier dans une transaction unique.
     * Le trigger BDD veille au respect de la capacite max.
     */
    public void save(Booking booking) {
        Connection cn = null;
        try {
            cn = ConnectionManager.getConnection();
            cn.setAutoCommit(false);

            int workshopId = resolveWorkshopId(cn, booking.getWorkshop());
            int memberId = resolveMemberId(cn, booking.getMember());

            try (PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, workshopId);
                ps.setInt(2, memberId);
                ps.setTimestamp(3, booking.getBookingDate() != null
                        ? Timestamp.valueOf(booking.getBookingDate())
                        : Timestamp.valueOf(java.time.LocalDateTime.now()));
                ps.setString(4, booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "PENDING");
                ps.executeUpdate();
            }
            cn.commit();
        } catch (SQLException e) {
            rollback(cn);
            throw new DataAccessException("Erreur save Booking : " + e.getMessage(), e);
        } finally {
            close(cn);
        }
    }

    public List<Booking> findByMember(CommunityMember member) {
        List<Booking> result = new ArrayList<>();
        Integer memberId = registry.getId(CommunityMember.class, member);
        if (memberId == null) {
            // Tenter une resolution par nom
            try (Connection cn = ConnectionManager.getConnection()) {
                memberId = lookupMemberIdByName(cn, member.getName());
                if (memberId != null) registry.register(CommunityMember.class, member, memberId);
            } catch (SQLException e) {
                throw new DataAccessException("Erreur lookup member id", e);
            }
        }
        if (memberId == null) return result;

        Map<Integer, com.project.artconnect.model.Artist> artistCache = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_MEMBER)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs, member, artistCache));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByMember Booking", e);
        }
        return result;
    }

    private Booking mapRow(ResultSet rs, CommunityMember member,
                           Map<Integer, com.project.artconnect.model.Artist> cache) throws SQLException {
        int instructorId = rs.getInt("w_instructor");
        com.project.artconnect.model.Artist instructor = cache.get(instructorId);
        if (instructor == null) {
            instructor = new com.project.artconnect.model.Artist(
                    rs.getString("a_name"),
                    rs.getString("a_bio"),
                    (Integer) rs.getObject("a_birth"),
                    rs.getString("a_email"),
                    rs.getString("a_city"));
            registry.register(com.project.artconnect.model.Artist.class, instructor, instructorId);
            cache.put(instructorId, instructor);
        }

        Timestamp wDate = rs.getTimestamp("w_date");
        Workshop w = new Workshop(
                rs.getString("w_title"),
                wDate != null ? wDate.toLocalDateTime() : null,
                instructor,
                rs.getDouble("w_price"));
        w.setDurationMinutes(rs.getInt("w_duration"));
        w.setMaxParticipants(rs.getInt("w_max"));
        w.setLocation(rs.getString("w_loc"));
        w.setDescription(rs.getString("w_desc"));
        String lvl = rs.getString("w_level");
        if (lvl != null) w.setLevel(Character.toUpperCase(lvl.charAt(0)) + lvl.substring(1));
        registry.register(Workshop.class, w, rs.getInt("w_id"));

        Booking b = new Booking(w, member);
        Timestamp bDate = rs.getTimestamp("booking_date");
        if (bDate != null) b.setBookingDate(bDate.toLocalDateTime());
        b.setPaymentStatus(rs.getString("payment_status"));
        return b;
    }

    private int resolveWorkshopId(Connection cn, Workshop w) throws SQLException {
        Integer id = registry.getId(Workshop.class, w);
        if (id != null) return id;
        try (PreparedStatement ps = cn.prepareStatement(SQL_WORKSHOP_ID_BY_TITLE)) {
            ps.setString(1, w.getTitle());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int found = rs.getInt(1);
                    registry.register(Workshop.class, w, found);
                    return found;
                }
            }
        }
        throw new SQLException("Workshop introuvable : " + w.getTitle());
    }

    private int resolveMemberId(Connection cn, CommunityMember m) throws SQLException {
        Integer id = registry.getId(CommunityMember.class, m);
        if (id != null) return id;
        Integer found = lookupMemberIdByName(cn, m.getName());
        if (found != null) {
            registry.register(CommunityMember.class, m, found);
            return found;
        }
        throw new SQLException("Membre introuvable : " + m.getName());
    }

    private Integer lookupMemberIdByName(Connection cn, String name) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(SQL_MEMBER_ID_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    private static void rollback(Connection cn) {
        if (cn != null) {
            try { cn.rollback(); } catch (SQLException ignored) {}
        }
    }

    private static void close(Connection cn) {
        if (cn != null) {
            try { cn.setAutoCommit(true); } catch (SQLException ignored) {}
            try { cn.close(); } catch (SQLException ignored) {}
        }
    }
}
