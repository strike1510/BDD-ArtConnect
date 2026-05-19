package com.project.artconnect.service.impl;

import com.project.artconnect.dao.WorkshopDao;
import com.project.artconnect.model.Booking;
import com.project.artconnect.model.CommunityMember;
import com.project.artconnect.model.Workshop;
import com.project.artconnect.persistence.JdbcBookingDao;
import com.project.artconnect.service.WorkshopService;

import java.util.List;
import java.util.Optional;

public class JdbcWorkshopService implements WorkshopService {

    private final WorkshopDao workshopDao;
    private final JdbcBookingDao bookingDao;

    public JdbcWorkshopService(WorkshopDao workshopDao, JdbcBookingDao bookingDao) {
        this.workshopDao = workshopDao;
        this.bookingDao = bookingDao;
    }

    @Override
    public List<Workshop> getAllWorkshops() {
        return workshopDao.findAll();
    }

    @Override
    public Optional<Workshop> getWorkshopByTitle(String title) {
        if (title == null) return Optional.empty();
        return workshopDao.findAll().stream()
                .filter(w -> title.equals(w.getTitle()))
                .findFirst();
    }

    @Override
    public void bookWorkshop(Workshop workshop, CommunityMember member) {
        if (workshop == null || member == null) return;
        Booking b = new Booking(workshop, member);
        bookingDao.save(b); // gere la transaction et le trigger SQL anti-overbooking
        member.addBooking(b);
    }

    @Override
    public List<Booking> getBookingsByMember(CommunityMember member) {
        return bookingDao.findByMember(member);
    }
}
