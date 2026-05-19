package com.project.artconnect.persistence;

import com.project.artconnect.model.Artwork;
import com.project.artconnect.model.CommunityMember;
import com.project.artconnect.model.Review;
import com.project.artconnect.util.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO JDBC pour Review (lie un membre a une oeuvre).
 * 
 * Pas d'interface dediee dans le squelette : ce DAO est consomme directement
 * par JdbcCommunityService (qui charge les avis d'un membre).
 */
public class JdbcReviewDao {

    private static final String SQL_FIND_BY_MEMBER =
            "SELECT r.id, r.rating, r.comment, r.review_date, " +
            "       aw.id AS aw_id, aw.title AS aw_title, aw.creation_year AS aw_year, " +
            "       aw.type AS aw_type, aw.price AS aw_price, aw.status AS aw_status, " +
            "       a.id AS a_id, a.name AS a_name, a.bio AS a_bio, a.birth_year AS a_birth, " +
            "       a.contact_email AS a_email, a.city AS a_city " +
            "FROM review r " +
            "JOIN artwork aw ON aw.id = r.artwork_id " +
            "JOIN artist a ON a.id = aw.artist_id " +
            "WHERE r.reviewer_id = ? ORDER BY r.review_date DESC";

    private static final String SQL_INSERT =
            "INSERT INTO review (reviewer_id, artwork_id, rating, comment, review_date) " +
            "VALUES (?, ?, ?, ?, ?)";

    private final IdentityRegistry registry = IdentityRegistry.getInstance();

    public List<Review> findByMember(CommunityMember member) {
        List<Review> result = new ArrayList<>();
        Integer memberId = registry.getId(CommunityMember.class, member);
        if (memberId == null) return result;
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_FIND_BY_MEMBER)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs, member));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Erreur findByMember Review", e);
        }
        return result;
    }

    public void save(Review review) {
        Integer reviewerId = registry.getId(CommunityMember.class, review.getReviewer());
        Integer artworkId = registry.getId(Artwork.class, review.getArtwork());
        if (reviewerId == null || artworkId == null) {
            throw new DataAccessException("Reviewer ou Artwork non resolu en BDD pour Review");
        }
        try (Connection cn = ConnectionManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, reviewerId);
            ps.setInt(2, artworkId);
            ps.setInt(3, review.getRating());
            ps.setString(4, review.getComment());
            ps.setDate(5, review.getReviewDate() != null ? Date.valueOf(review.getReviewDate()) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Erreur save Review", e);
        }
    }

    private Review mapRow(ResultSet rs, CommunityMember reviewer) throws SQLException {
        // Reconstruire une vue minimale de l'oeuvre (et son artiste) pour la review
        com.project.artconnect.model.Artist artist = new com.project.artconnect.model.Artist(
                rs.getString("a_name"),
                rs.getString("a_bio"),
                (Integer) rs.getObject("a_birth"),
                rs.getString("a_email"),
                rs.getString("a_city"));
        registry.register(com.project.artconnect.model.Artist.class, artist, rs.getInt("a_id"));

        Artwork aw = new Artwork(
                rs.getString("aw_title"),
                (Integer) rs.getObject("aw_year"),
                rs.getString("aw_type"),
                rs.getDouble("aw_price"),
                artist);
        try {
            String s = rs.getString("aw_status");
            if (s != null) aw.setStatus(Artwork.Status.valueOf(s));
        } catch (IllegalArgumentException ignored) {}
        registry.register(Artwork.class, aw, rs.getInt("aw_id"));

        Review r = new Review(reviewer, aw, rs.getInt("rating"), rs.getString("comment"));
        Date d = rs.getDate("review_date");
        if (d != null) r.setReviewDate(d.toLocalDate());
        return r;
    }
}
