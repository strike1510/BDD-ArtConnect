package com.project.artconnect.persistence;

import com.project.artconnect.dao.ArtistDao;
import com.project.artconnect.model.Artist;
import com.project.artconnect.model.Discipline;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation JDBC du DAO Artist.
 * 
 * Gere la table artist + la table de jointure artist_discipline.
 * Tient a jour le IdentityRegistry pour pouvoir retrouver l'id BDD d'un
 * objet Artist Java sans modifier la classe model.
 */
public class JdbcArtistDao implements ArtistDao {

    private static final String SQL_FIND_ALL =
            "SELECT id, name, bio, birth_year, contact_email, phone, city, website, social_media, is_active " +
            "FROM artist ORDER BY name";

    private static final String SQL_FIND_BY_CITY =
            "SELECT id, name, bio, birth_year, contact_email, phone, city, website, social_media, is_active " +
            "FROM artist WHERE city = ? ORDER BY name";

    private static final String SQL_INSERT =
            "INSERT INTO artist (name, bio, birth_year, contact_email, phone, city, website, social_media, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE artist SET bio = ?, birth_year = ?, contact_email = ?, phone = ?, city = ?, " +
            "website = ?, social_media = ?, is_active = ? WHERE name = ?";

    private static final String SQL_DELETE = "DELETE FROM artist WHERE name = ?";

    private static final String SQL_FIND_DISCIPLINES_BY_ARTIST =
            "SELECT d.id, d.name FROM discipline d " +
            "JOIN artist_discipline ad ON ad.discipline_id = d.id " +
            "WHERE ad.artist_id = ?";

    private static final String SQL_INSERT_LINK =
            "INSERT INTO artist_discipline (artist_id, discipline_id) VALUES (?, ?)";

    private static final String SQL_DELETE_LINKS =
            "DELETE FROM artist_discipline WHERE artist_id = ?";

    private static final String SQL_FIND_DISCIPLINE_BY_NAME =
            "SELECT id FROM discipline WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public List<Artist> findAll() {
        List<Artist> result = new ArrayList<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs, cn));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Artist", e);
        }
        return result;
    }

    @Override
    public List<Artist> findByCity(String city) {
        List<Artist> result = new ArrayList<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_CITY)) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs, cn));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByCity Artist", e);
        }
        return result;
    }

    @Override
    public void save(Artist artist) {
        Connection cn = null;
        try {
            cn = ConnectionManager.getConnection();
            cn.setAutoCommit(false);

            int generatedId;
            try (PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                bindArtistInsert(ps, artist);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Aucun id genere pour l'artiste " + artist.getName());
                    }
                    generatedId = keys.getInt(1);
                }
            }
            registry.register(Artist.class, artist, generatedId);

            // Liaison aux disciplines (creation des entrees manquantes si necessaire)
            persistDisciplineLinks(cn, artist, generatedId);

            cn.commit();
        } catch (SQLException e) {
            rollback(cn);
            throw new DataAccessException("Erreur save Artist", e);
        } finally {
            close(cn);
        }
    }

    @Override
    public void update(Artist artist) {
        Connection cn = null;
        try {
            cn = ConnectionManager.getConnection();
            cn.setAutoCommit(false);

            try (PreparedStatement ps = cn.prepareStatement(SQL_UPDATE)) {
                ps.setString(1, artist.getBio());
                setNullableInt(ps, 2, artist.getBirthYear());
                ps.setString(3, artist.getContactEmail());
                ps.setString(4, artist.getPhone());
                ps.setString(5, artist.getCity());
                ps.setString(6, artist.getWebsite());
                ps.setString(7, artist.getSocialMedia());
                ps.setBoolean(8, artist.isActive());
                ps.setString(9, artist.getName());
                ps.executeUpdate();
            }

            // Recuperer l'id de l'artiste (cache ou re-lookup)
            Integer id = registry.getId(Artist.class, artist);
            if (id == null) {
                id = lookupIdByName(cn, artist.getName());
                if (id != null) {
                    registry.register(Artist.class, artist, id);
                }
            }
            if (id != null) {
                // Strategie simple : on supprime puis recree les liens disciplines
                try (PreparedStatement del = cn.prepareStatement(SQL_DELETE_LINKS)) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                persistDisciplineLinks(cn, artist, id);
            }

            cn.commit();
        } catch (SQLException e) {
            rollback(cn);
            throw new DataAccessException("Erreur update Artist", e);
        } finally {
            close(cn);
        }
    }

    @Override
    public void delete(String artistName) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_DELETE)) {
            ps.setString(1, artistName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur delete Artist", e);
        }
    }

    // ---------------- helpers ----------------

    private Artist mapRow(ResultSet rs, Connection cn) throws SQLException {
        int id = rs.getInt("id");
        Artist a = new Artist(
                rs.getString("name"),
                rs.getString("bio"),
                (Integer) rs.getObject("birth_year"),
                rs.getString("contact_email"),
                rs.getString("city"));
        a.setPhone(rs.getString("phone"));
        a.setWebsite(rs.getString("website"));
        a.setSocialMedia(rs.getString("social_media"));
        a.setActive(rs.getBoolean("is_active"));

        // Charger les disciplines associees
        try (PreparedStatement ps = cn.prepareStatement(SQL_FIND_DISCIPLINES_BY_ARTIST)) {
            ps.setInt(1, id);
            try (ResultSet rsD = ps.executeQuery()) {
                while (rsD.next()) {
                    Discipline d = new Discipline(rsD.getString("name"));
                    registry.register(Discipline.class, d, rsD.getInt("id"));
                    a.getDisciplines().add(d);
                }
            }
        }

        registry.register(Artist.class, a, id);
        return a;
    }

    private void bindArtistInsert(PreparedStatement ps, Artist artist) throws SQLException {
        ps.setString(1, artist.getName());
        ps.setString(2, artist.getBio());
        setNullableInt(ps, 3, artist.getBirthYear());
        ps.setString(4, artist.getContactEmail());
        ps.setString(5, artist.getPhone());
        ps.setString(6, artist.getCity());
        ps.setString(7, artist.getWebsite());
        ps.setString(8, artist.getSocialMedia());
        ps.setBoolean(9, artist.isActive());
    }

    private void persistDisciplineLinks(Connection cn, Artist artist, int artistId) throws SQLException {
        if (artist.getDisciplines() == null || artist.getDisciplines().isEmpty()) {
            return;
        }
        // Map discipline name -> id, en lookup ou en creation
        Map<String, Integer> disciplineIds = new HashMap<>();
        for (Discipline d : artist.getDisciplines()) {
            Integer did = registry.getId(Discipline.class, d);
            if (did == null) {
                did = lookupOrCreateDisciplineId(cn, d.getName());
                registry.register(Discipline.class, d, did);
            }
            disciplineIds.put(d.getName(), did);
        }

        try (PreparedStatement ps = cn.prepareStatement(SQL_INSERT_LINK)) {
            for (Integer did : disciplineIds.values()) {
                ps.setInt(1, artistId);
                ps.setInt(2, did);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private int lookupOrCreateDisciplineId(Connection cn, String name) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(SQL_FIND_DISCIPLINE_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO discipline (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Impossible de creer ou retrouver la discipline " + name);
    }

    private Integer lookupIdByName(Connection cn, String name) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM artist WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
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
