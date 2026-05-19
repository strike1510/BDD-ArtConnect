package com.project.artconnect.persistence;

import com.project.artconnect.dao.ArtworkDao;
import com.project.artconnect.model.Artist;
import com.project.artconnect.model.Artwork;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation JDBC du DAO Artwork.
 * 
 * Reconstruit le lien Artwork -> Artist via une jointure SQL et conserve
 * l'identite des objets Artist deja chargees grace au IdentityRegistry.
 */
public class JdbcArtworkDao implements ArtworkDao {

    private static final String SQL_FIND_ALL =
            "SELECT aw.id, aw.title, aw.creation_year, aw.type, aw.medium, aw.dimensions, " +
            "       aw.description, aw.price, aw.status, aw.artist_id, " +
            "       a.name AS artist_name, a.bio AS artist_bio, a.birth_year AS artist_birth, " +
            "       a.contact_email AS artist_email, a.city AS artist_city, " +
            "       a.phone AS artist_phone, a.website AS artist_website, " +
            "       a.social_media AS artist_social, a.is_active AS artist_active " +
            "FROM artwork aw " +
            "JOIN artist a ON a.id = aw.artist_id " +
            "ORDER BY aw.title";

    private static final String SQL_FIND_BY_ARTIST_NAME =
            "SELECT aw.id, aw.title, aw.creation_year, aw.type, aw.medium, aw.dimensions, " +
            "       aw.description, aw.price, aw.status, aw.artist_id, " +
            "       a.name AS artist_name, a.bio AS artist_bio, a.birth_year AS artist_birth, " +
            "       a.contact_email AS artist_email, a.city AS artist_city, " +
            "       a.phone AS artist_phone, a.website AS artist_website, " +
            "       a.social_media AS artist_social, a.is_active AS artist_active " +
            "FROM artwork aw " +
            "JOIN artist a ON a.id = aw.artist_id " +
            "WHERE a.name = ? ORDER BY aw.title";

    private static final String SQL_INSERT =
            "INSERT INTO artwork (title, creation_year, type, medium, dimensions, description, price, status, artist_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE artwork SET creation_year = ?, type = ?, medium = ?, dimensions = ?, description = ?, " +
            "price = ?, status = ?, artist_id = ? WHERE title = ?";

    private static final String SQL_DELETE = "DELETE FROM artwork WHERE title = ?";

    private static final String SQL_FIND_ARTIST_ID_BY_NAME = "SELECT id FROM artist WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public List<Artwork> findAll() {
        List<Artwork> result = new ArrayList<>();
        // Cache locale pour eviter de recreer plusieurs Artist Java pour le meme id BDD
        Map<Integer, Artist> artistsLoaded = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs, artistsLoaded));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Artwork", e);
        }
        return result;
    }

    @Override
    public List<Artwork> findByArtistName(String artistName) {
        List<Artwork> result = new ArrayList<>();
        Map<Integer, Artist> artistsLoaded = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_ARTIST_NAME)) {
            ps.setString(1, artistName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs, artistsLoaded));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByArtistName Artwork", e);
        }
        return result;
    }

    @Override
    public void save(Artwork artwork) {
        if (artwork.getArtist() == null) {
            throw new DataAccessException("Impossible de sauver une oeuvre sans artiste : " + artwork.getTitle());
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            int artistId = resolveArtistId(cn, artwork.getArtist());
            bindInsert(ps, artwork, artistId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    registry.register(Artwork.class, artwork, keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur save Artwork", e);
        }
    }

    @Override
    public void update(Artwork artwork) {
        if (artwork.getArtist() == null) {
            throw new DataAccessException("Impossible d'updater une oeuvre sans artiste : " + artwork.getTitle());
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_UPDATE)) {
            int artistId = resolveArtistId(cn, artwork.getArtist());
            setNullableInt(ps, 1, artwork.getCreationYear());
            ps.setString(2, artwork.getType());
            ps.setString(3, artwork.getMedium());
            ps.setString(4, artwork.getDimensions());
            ps.setString(5, artwork.getDescription());
            ps.setDouble(6, artwork.getPrice());
            ps.setString(7, artwork.getStatus() != null ? artwork.getStatus().name() : Artwork.Status.FOR_SALE.name());
            ps.setInt(8, artistId);
            ps.setString(9, artwork.getTitle());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur update Artwork", e);
        }
    }

    @Override
    public void delete(String title) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_DELETE)) {
            ps.setString(1, title);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur delete Artwork", e);
        }
    }

    // ---------------- helpers ----------------

    private Artwork mapRow(ResultSet rs, Map<Integer, Artist> cache) throws SQLException {
        int artistId = rs.getInt("artist_id");
        Artist artist = cache.get(artistId);
        if (artist == null) {
            artist = new Artist(
                    rs.getString("artist_name"),
                    rs.getString("artist_bio"),
                    (Integer) rs.getObject("artist_birth"),
                    rs.getString("artist_email"),
                    rs.getString("artist_city"));
            artist.setPhone(rs.getString("artist_phone"));
            artist.setWebsite(rs.getString("artist_website"));
            artist.setSocialMedia(rs.getString("artist_social"));
            artist.setActive(rs.getBoolean("artist_active"));
            registry.register(Artist.class, artist, artistId);
            cache.put(artistId, artist);
        }

        Artwork aw = new Artwork(
                rs.getString("title"),
                (Integer) rs.getObject("creation_year"),
                rs.getString("type"),
                rs.getDouble("price"),
                artist);
        aw.setMedium(rs.getString("medium"));
        aw.setDimensions(rs.getString("dimensions"));
        aw.setDescription(rs.getString("description"));
        String status = rs.getString("status");
        if (status != null) {
            try {
                aw.setStatus(Artwork.Status.valueOf(status));
            } catch (IllegalArgumentException ignored) {
                aw.setStatus(Artwork.Status.FOR_SALE);
            }
        }
        artist.addArtwork(aw);
        registry.register(Artwork.class, aw, rs.getInt("id"));
        return aw;
    }

    private void bindInsert(PreparedStatement ps, Artwork artwork, int artistId) throws SQLException {
        ps.setString(1, artwork.getTitle());
        setNullableInt(ps, 2, artwork.getCreationYear());
        ps.setString(3, artwork.getType());
        ps.setString(4, artwork.getMedium());
        ps.setString(5, artwork.getDimensions());
        ps.setString(6, artwork.getDescription());
        ps.setDouble(7, artwork.getPrice());
        ps.setString(8, artwork.getStatus() != null ? artwork.getStatus().name() : Artwork.Status.FOR_SALE.name());
        ps.setInt(9, artistId);
    }

    private int resolveArtistId(Connection cn, Artist artist) throws SQLException {
        Integer id = registry.getId(Artist.class, artist);
        if (id != null) return id;
        try (PreparedStatement ps = cn.prepareStatement(SQL_FIND_ARTIST_ID_BY_NAME)) {
            ps.setString(1, artist.getName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int found = rs.getInt(1);
                    registry.register(Artist.class, artist, found);
                    return found;
                }
            }
        }
        throw new SQLException("Artiste introuvable en base : " + artist.getName());
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }
}
