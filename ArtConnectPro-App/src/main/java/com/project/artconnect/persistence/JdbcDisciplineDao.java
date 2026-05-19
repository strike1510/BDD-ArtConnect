package com.project.artconnect.persistence;

import com.project.artconnect.dao.DisciplineDao;
import com.project.artconnect.model.Discipline;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation JDBC du DAO Discipline.
 */
public class JdbcDisciplineDao implements DisciplineDao {

    private static final String SQL_FIND_ALL = "SELECT id, name FROM discipline ORDER BY name";
    private static final String SQL_FIND_BY_NAME = "SELECT id, name FROM discipline WHERE name = ?";
    private static final String SQL_INSERT = "INSERT INTO discipline (name) VALUES (?)";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public List<Discipline> findAll() {
        List<Discipline> result = new ArrayList<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Discipline d = mapRow(rs);
                result.add(d);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll Discipline", e);
        }
        return result;
    }

    @Override
    public Optional<Discipline> findByName(String name) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByName Discipline", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(Discipline discipline) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, discipline.getName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    registry.register(Discipline.class, discipline, keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur save Discipline", e);
        }
    }

    private Discipline mapRow(ResultSet rs) throws SQLException {
        Discipline d = new Discipline(rs.getString("name"));
        registry.register(Discipline.class, d, rs.getInt("id"));
        return d;
    }
}
