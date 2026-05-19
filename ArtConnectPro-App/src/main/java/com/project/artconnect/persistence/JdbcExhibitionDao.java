package com.project.artconnect.persistence;

import com.project.artconnect.dao.ExhibitionDao;
import com.project.artconnect.model.Exhibition;
import com.project.artconnect.model.Gallery;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation JDBC du DAO Exhibition.
 * Reconstruit le lien Exhibition -> Gallery et restitue la liaison
 * bidirectionnelle (Gallery.addExhibition).
 */
public class JdbcExhibitionDao implements ExhibitionDao {

    private static final String SQL_FIND_ALL =
            "SELECT e.id, e.title, e.start_date, e.end_date, e.description, e.curator_name, e.theme, " +
            "       e.gallery_id, g.name AS g_name, g.address AS g_address, g.rating AS g_rating, " +
            "       g.owner_name AS g_owner, g.opening_hours AS g_hours, " +
            "       g.contact_phone AS g_phone, g.website AS g_website " +
            "FROM exhibition e JOIN gallery g ON g.id = e.gallery_id " +
            "ORDER BY e.start_date DESC";

    private static final String SQL_INSERT =
            "INSERT INTO exhibition (title, start_date, end_date, description, gallery_id, curator_name, theme) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE exhibition SET start_date = ?, end_date = ?, description = ?, gallery_id = ?, " +
            "curator_name = ?, theme = ? WHERE title = ?";

    private static final String SQL_DELETE = "DELETE FROM exhibition WHERE title = ?";

    private static final String SQL_GALLERY_ID_BY_NAME = "SELECT id FROM gallery WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public List<Exhibition> findAll() {
        List<Exhibition> result = new ArrayList<>();
        Map<Integer, Gallery> galleryCache = new HashMap<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs, galleryCache));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Exhibition", e);
        }
        return result;
    }

    @Override
    public void save(Exhibition exhibition) {
        if (exhibition.getGallery() == null) {
            throw new DataAccessException("Impossible de sauver une exposition sans galerie : " + exhibition.getTitle());
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            int gid = resolveGalleryId(cn, exhibition.getGallery());
            ps.setString(1, exhibition.getTitle());
            ps.setDate(2, exhibition.getStartDate() != null ? Date.valueOf(exhibition.getStartDate()) : null);
            ps.setDate(3, exhibition.getEndDate() != null ? Date.valueOf(exhibition.getEndDate()) : null);
            ps.setString(4, exhibition.getDescription());
            ps.setInt(5, gid);
            ps.setString(6, exhibition.getCuratorName());
            ps.setString(7, exhibition.getTheme());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) registry.register(Exhibition.class, exhibition, keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur save Exhibition", e);
        }
    }

    @Override
    public void update(Exhibition exhibition) {
        if (exhibition.getGallery() == null) {
            throw new DataAccessException("Galerie obligatoire pour update Exhibition : " + exhibition.getTitle());
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_UPDATE)) {
            int gid = resolveGalleryId(cn, exhibition.getGallery());
            ps.setDate(1, exhibition.getStartDate() != null ? Date.valueOf(exhibition.getStartDate()) : null);
            ps.setDate(2, exhibition.getEndDate() != null ? Date.valueOf(exhibition.getEndDate()) : null);
            ps.setString(3, exhibition.getDescription());
            ps.setInt(4, gid);
            ps.setString(5, exhibition.getCuratorName());
            ps.setString(6, exhibition.getTheme());
            ps.setString(7, exhibition.getTitle());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur update Exhibition", e);
        }
    }

    @Override
    public void delete(String title) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_DELETE)) {
            ps.setString(1, title);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur delete Exhibition", e);
        }
    }

    // ---------------- helpers ----------------

    private Exhibition mapRow(ResultSet rs, Map<Integer, Gallery> cache) throws SQLException {
        int gid = rs.getInt("gallery_id");
        Gallery gallery = cache.get(gid);
        if (gallery == null) {
            gallery = new Gallery(rs.getString("g_name"), rs.getString("g_address"), rs.getDouble("g_rating"));
            gallery.setOwnerName(rs.getString("g_owner"));
            gallery.setOpeningHours(rs.getString("g_hours"));
            gallery.setContactPhone(rs.getString("g_phone"));
            gallery.setWebsite(rs.getString("g_website"));
            registry.register(Gallery.class, gallery, gid);
            cache.put(gid, gallery);
        }

        Exhibition e = new Exhibition(
                rs.getString("title"),
                rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null,
                rs.getDate("end_date") != null ? rs.getDate("end_date").toLocalDate() : null,
                gallery);
        e.setDescription(rs.getString("description"));
        e.setCuratorName(rs.getString("curator_name"));
        e.setTheme(rs.getString("theme"));

        gallery.addExhibition(e); // restitue la liaison bidirectionnelle
        registry.register(Exhibition.class, e, rs.getInt("id"));
        return e;
    }

    private int resolveGalleryId(Connection cn, Gallery gallery) throws SQLException {
        Integer id = registry.getId(Gallery.class, gallery);
        if (id != null) return id;
        try (PreparedStatement ps = cn.prepareStatement(SQL_GALLERY_ID_BY_NAME)) {
            ps.setString(1, gallery.getName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int found = rs.getInt(1);
                    registry.register(Gallery.class, gallery, found);
                    return found;
                }
            }
        }
        throw new SQLException("Galerie introuvable en base : " + gallery.getName());
    }
}
