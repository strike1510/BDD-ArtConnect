package com.project.artconnect.persistence;

import com.project.artconnect.dao.GalleryDao;
import com.project.artconnect.model.Gallery;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation JDBC du DAO Gallery.
 */
public class JdbcGalleryDao implements GalleryDao {

    private static final String SQL_FIND_ALL =
            "SELECT id, name, address, owner_name, opening_hours, contact_phone, rating, website " +
            "FROM gallery ORDER BY name";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, name, address, owner_name, opening_hours, contact_phone, rating, website " +
            "FROM gallery WHERE id = ?";

    private static final String SQL_FIND_BY_NAME =
            "SELECT id, name, address, owner_name, opening_hours, contact_phone, rating, website " +
            "FROM gallery WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public Optional<Gallery> findById(Long id) {
        if (id == null) return Optional.empty();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findById Gallery", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Gallery> findAll() {
        List<Gallery> result = new ArrayList<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Gallery", e);
        }
        return result;
    }

    /** Methode complementaire utile aux services pour resoudre l'id BDD via le nom. */
    public Optional<Gallery> findByName(String name) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByName Gallery", e);
        }
        return Optional.empty();
    }

    private Gallery mapRow(ResultSet rs) throws SQLException {
        Gallery g = new Gallery(rs.getString("name"), rs.getString("address"), rs.getDouble("rating"));
        g.setOwnerName(rs.getString("owner_name"));
        g.setOpeningHours(rs.getString("opening_hours"));
        g.setContactPhone(rs.getString("contact_phone"));
        g.setWebsite(rs.getString("website"));
        registry.register(Gallery.class, g, rs.getInt("id"));
        return g;
    }
}
