package com.project.artconnect.persistence;

import com.project.artconnect.dao.WorkshopDao;
import com.project.artconnect.model.Artist;
import com.project.artconnect.model.Workshop;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation JDBC du DAO Workshop.
 * Reconstruit le lien Workshop -> Artist (instructeur).
 */
public class JdbcWorkshopDao implements WorkshopDao {

    private static final String SQL_FIND_ALL =
            "SELECT w.id, w.title, w.date, w.duration_minutes, w.max_participants, w.price, " +
            "       w.location, w.description, w.level, w.instructor_id, " +
            "       a.name AS a_name, a.bio AS a_bio, a.birth_year AS a_birth, " +
            "       a.contact_email AS a_email, a.city AS a_city " +
            "FROM workshop w JOIN artist a ON a.id = w.instructor_id " +
            "ORDER BY w.date";

    private static final String SQL_FIND_BY_ID =
            "SELECT w.id, w.title, w.date, w.duration_minutes, w.max_participants, w.price, " +
            "       w.location, w.description, w.level, w.instructor_id, " +
            "       a.name AS a_name, a.bio AS a_bio, a.birth_year AS a_birth, " +
            "       a.contact_email AS a_email, a.city AS a_city " +
            "FROM workshop w JOIN artist a ON a.id = w.instructor_id " +
            "WHERE w.id = ?";

    private static final String SQL_INSERT =
            "INSERT INTO workshop (title, date, duration_minutes, max_participants, price, instructor_id, " +
            "location, description, level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FIND_ARTIST_ID = "SELECT id FROM artist WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public Optional<Workshop> findById(Long id) {
        if (id == null) return Optional.empty();
        Map<Integer, Artist> artistCache = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs, artistCache));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findById Workshop", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Workshop> findAll() {
        List<Workshop> result = new ArrayList<>();
        Map<Integer, Artist> artistCache = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs, artistCache));
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Workshop", e);
        }
        return result;
    }

    /** Methode complementaire utilisee par le service pour creer un workshop. */
    public void save(Workshop workshop) {
        if (workshop.getInstructor() == null) {
            throw new DataAccessException("Workshop sans instructeur : " + workshop.getTitle());
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            int instructorId = resolveArtistId(cn, workshop.getInstructor());
            ps.setString(1, workshop.getTitle());
            ps.setTimestamp(2, workshop.getDate() != null ? Timestamp.valueOf(workshop.getDate()) : null);
            ps.setInt(3, workshop.getDurationMinutes());
            ps.setInt(4, workshop.getMaxParticipants());
            ps.setDouble(5, workshop.getPrice());
            ps.setInt(6, instructorId);
            ps.setString(7, workshop.getLocation());
            ps.setString(8, workshop.getDescription());
            // BD : enum minuscule (beginner/intermediate/advanced)
            ps.setString(9, workshop.getLevel() != null ? workshop.getLevel().toLowerCase() : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) registry.register(Workshop.class, workshop, keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur save Workshop", e);
        }
    }

    // ---------------- helpers ----------------

    private Workshop mapRow(ResultSet rs, Map<Integer, Artist> cache) throws SQLException {
        int instructorId = rs.getInt("instructor_id");
        Artist instructor = cache.get(instructorId);
        if (instructor == null) {
            instructor = new Artist(
                    rs.getString("a_name"),
                    rs.getString("a_bio"),
                    (Integer) rs.getObject("a_birth"),
                    rs.getString("a_email"),
                    rs.getString("a_city"));
            registry.register(Artist.class, instructor, instructorId);
            cache.put(instructorId, instructor);
        }

        Timestamp ts = rs.getTimestamp("date");
        Workshop w = new Workshop(
                rs.getString("title"),
                ts != null ? ts.toLocalDateTime() : null,
                instructor,
                rs.getDouble("price"));
        w.setDurationMinutes(rs.getInt("duration_minutes"));
        w.setMaxParticipants(rs.getInt("max_participants"));
        w.setLocation(rs.getString("location"));
        w.setDescription(rs.getString("description"));
        String level = rs.getString("level");
        if (level != null) {
            // L'UI affiche en CamelCase ("Beginner") pour rester coherente avec l'existant
            w.setLevel(Character.toUpperCase(level.charAt(0)) + level.substring(1));
        }
        registry.register(Workshop.class, w, rs.getInt("id"));
        return w;
    }

    private int resolveArtistId(Connection cn, Artist artist) throws SQLException {
        Integer id = registry.getId(Artist.class, artist);
        if (id != null) return id;
        try (PreparedStatement ps = cn.prepareStatement(SQL_FIND_ARTIST_ID)) {
            ps.setString(1, artist.getName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int found = rs.getInt(1);
                    registry.register(Artist.class, artist, found);
                    return found;
                }
            }
        }
        throw new SQLException("Artiste instructeur introuvable : " + artist.getName());
    }
}
