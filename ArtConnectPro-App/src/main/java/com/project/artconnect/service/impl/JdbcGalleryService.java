package com.project.artconnect.service.impl;

import com.project.artconnect.dao.ExhibitionDao;
import com.project.artconnect.dao.GalleryDao;
import com.project.artconnect.model.Exhibition;
import com.project.artconnect.model.Gallery;
import com.project.artconnect.service.GalleryService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcGalleryService implements GalleryService {

    private final GalleryDao galleryDao;
    private final ExhibitionDao exhibitionDao;

    public JdbcGalleryService(GalleryDao galleryDao, ExhibitionDao exhibitionDao) {
        this.galleryDao = galleryDao;
        this.exhibitionDao = exhibitionDao;
    }

    @Override
    public List<Gallery> getAllGalleries() {
        // Charger les galeries puis attacher leurs expositions pour conserver
        // la liaison bidirectionnelle attendue par l'UI Discover.
        List<Gallery> galleries = galleryDao.findAll();
        List<Exhibition> exhibitions = exhibitionDao.findAll();
        for (Exhibition e : exhibitions) {
            for (Gallery g : galleries) {
                if (g.getName() != null && g.getName().equals(e.getGallery().getName())
                        && g.getExhibitions().stream().noneMatch(x -> x.getTitle().equals(e.getTitle()))) {
                    g.addExhibition(e);
                }
            }
        }
        return galleries;
    }

    @Override
    public Optional<Gallery> getGalleryByName(String name) {
        if (name == null) return Optional.empty();
        return getAllGalleries().stream()
                .filter(g -> name.equals(g.getName()))
                .findFirst();
    }

    @Override
    public List<Exhibition> getExhibitionsByGallery(Gallery gallery) {
        if (gallery == null || gallery.getName() == null) return Collections.emptyList();
        return exhibitionDao.findAll().stream()
                .filter(e -> e.getGallery() != null && gallery.getName().equals(e.getGallery().getName()))
                .collect(Collectors.toList());
    }
}
