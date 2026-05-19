package com.project.artconnect.service.impl;

import com.project.artconnect.dao.CommunityMemberDao;
import com.project.artconnect.model.CommunityMember;
import com.project.artconnect.model.Review;
import com.project.artconnect.persistence.JdbcReviewDao;
import com.project.artconnect.service.CommunityService;

import java.util.List;
import java.util.Optional;

public class JdbcCommunityService implements CommunityService {

    private final CommunityMemberDao memberDao;
    private final JdbcReviewDao reviewDao;

    public JdbcCommunityService(CommunityMemberDao memberDao, JdbcReviewDao reviewDao) {
        this.memberDao = memberDao;
        this.reviewDao = reviewDao;
    }

    @Override
    public List<CommunityMember> getAllMembers() {
        return memberDao.findAll();
    }

    @Override
    public Optional<CommunityMember> getMemberByName(String name) {
        if (name == null) return Optional.empty();
        return memberDao.findAll().stream()
                .filter(m -> name.equals(m.getName()))
                .findFirst();
    }

    @Override
    public List<Review> getReviewsByMember(CommunityMember member) {
        return reviewDao.findByMember(member);
    }
}
