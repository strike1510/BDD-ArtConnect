package com.project.artconnect.util;

import com.project.artconnect.config.DatabaseConfig;
import com.project.artconnect.persistence.*;
import com.project.artconnect.service.*;
import com.project.artconnect.service.impl.*;

/**
 * Fournit les services de l'application selon le mode configure
 * (jdbc ou memory). En mode jdbc, on instancie les services JDBC qui
 * delegent aux DAO ; sinon on retombe sur les services in-memory deja fournis
 * dans le squelette pour la demo / les tests.
 */
public class ServiceProvider {

    private static final ArtistService artistService;
    private static final ArtworkService artworkService;
    private static final GalleryService galleryService;
    private static final WorkshopService workshopService;
    private static final CommunityService communityService;

    static {
        boolean useJdbc = "jdbc".equalsIgnoreCase(DatabaseConfig.APP_MODE);

        if (useJdbc) {
            System.out.println("[ServiceProvider] Mode JDBC actif (URL=" + DatabaseConfig.URL + ")");

            // DAO
            JdbcArtistDao artistDao = new JdbcArtistDao();
            JdbcDisciplineDao disciplineDao = new JdbcDisciplineDao();
            JdbcArtworkDao artworkDao = new JdbcArtworkDao();
            JdbcGalleryDao galleryDao = new JdbcGalleryDao();
            JdbcExhibitionDao exhibitionDao = new JdbcExhibitionDao();
            JdbcWorkshopDao workshopDao = new JdbcWorkshopDao();
            JdbcCommunityMemberDao memberDao = new JdbcCommunityMemberDao();
            JdbcBookingDao bookingDao = new JdbcBookingDao();
            JdbcReviewDao reviewDao = new JdbcReviewDao();

            // Services
            artistService = new JdbcArtistService(artistDao, disciplineDao);
            artworkService = new JdbcArtworkService(artworkDao);
            galleryService = new JdbcGalleryService(galleryDao, exhibitionDao);
            workshopService = new JdbcWorkshopService(workshopDao, bookingDao);
            communityService = new JdbcCommunityService(memberDao, reviewDao);

        } else {
            System.out.println("[ServiceProvider] Mode IN-MEMORY actif (donnees factices).");

            InMemoryArtistService inArtist = new InMemoryArtistService();
            InMemoryArtworkService inArtwork = new InMemoryArtworkService();
            InMemoryGalleryService inGallery = new InMemoryGalleryService();
            InMemoryWorkshopService inWorkshop = new InMemoryWorkshopService();
            InMemoryCommunityService inCommunity = new InMemoryCommunityService();

            inArtwork.initData(inArtist);
            inGallery.initData(inArtwork);
            inWorkshop.initData(inArtist);
            inCommunity.initData(inArtwork);

            artistService = inArtist;
            artworkService = inArtwork;
            galleryService = inGallery;
            workshopService = inWorkshop;
            communityService = inCommunity;
        }
    }

    public static ArtistService getArtistService()       { return artistService; }
    public static ArtworkService getArtworkService()     { return artworkService; }
    public static GalleryService getGalleryService()     { return galleryService; }
    public static WorkshopService getWorkshopService()   { return workshopService; }
    public static CommunityService getCommunityService() { return communityService; }

    private ServiceProvider() {}
}
