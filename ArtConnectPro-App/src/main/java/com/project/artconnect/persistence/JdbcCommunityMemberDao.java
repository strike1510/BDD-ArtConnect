package com.project.artconnect.persistence;

import com.project.artconnect.dao.CommunityMemberDao;
import com.project.artconnect.model.CommunityMember;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation JDBC du DAO CommunityMember.
 */
public class JdbcCommunityMemberDao implements CommunityMemberDao {

    private static final String SQL_FIND_ALL =
            "SELECT id, name, email, birth_year, phone, city, membership_type " +
            "FROM community_member ORDER BY name";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, name, email, birth_year, phone, city, membership_type " +
            "FROM community_member WHERE id = ?";

    private static final String SQL_FIND_BY_NAME =
            "SELECT id, name, email, birth_year, phone, city, membership_type " +
            "FROM community_member WHERE name = ?";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    @Override
    public Optional<CommunityMember> findById(Long id) {
        if (id == null) return Optional.empty();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findById CommunityMember", e);
        }
        return Optional.empty();
    }

    @Override
    public List<CommunityMember> findAll() {
        List<CommunityMember> result = new ArrayList<>();
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findAll CommunityMember", e);
        }
        return result;
    }

    public Optional<CommunityMember> findByName(String name) {
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByName CommunityMember", e);
        }
        return Optional.empty();
    }

    private CommunityMember mapRow(ResultSet rs) throws SQLException {
        CommunityMember m = new CommunityMember(rs.getString("name"), rs.getString("email"));
        m.setBirthYear((Integer) rs.getObject("birth_year"));
        m.setPhone(rs.getString("phone"));
        m.setCity(rs.getString("city"));
        m.setMembershipType(rs.getString("membership_type"));
        registry.register(CommunityMember.class, m, rs.getInt("id"));
        return m;
    }
}
