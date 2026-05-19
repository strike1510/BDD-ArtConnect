package com.project.artconnect.service.impl;

import com.project.artconnect.dao.ArtistDao;
import com.project.artconnect.dao.DisciplineDao;
import com.project.artconnect.model.Artist;
import com.project.artconnect.model.Discipline;
import com.project.artconnect.service.ArtistService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation du service Artist branchee sur la base de donnees via JDBC.
 * Le tri / la recherche par discipline / ville se font cote service en memoire
 * apres recuperation pour rester coherent avec le contrat existant.
 */
public class JdbcArtistService implements ArtistService {

    private final ArtistDao artistDao;
    private final DisciplineDao disciplineDao;

    public JdbcArtistService(ArtistDao artistDao, DisciplineDao disciplineDao) {
        this.artistDao = artistDao;
        this.disciplineDao = disciplineDao;
    }

    @Override
    public List<Artist> getAllArtists() {
        return artistDao.findAll();
    }

    @Override
    public Optional<Artist> getArtistByName(String name) {
        if (name == null) return Optional.empty();
        return artistDao.findAll().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst();
    }

    @Override
    public void createArtist(Artist artist) {
        artistDao.save(artist);
    }

    @Override
    public void updateArtist(Artist artist) {
        artistDao.update(artist);
    }

    @Override
    public void deleteArtist(String name) {
        artistDao.delete(name);
    }

    @Override
    public List<Discipline> getAllDisciplines() {
        return disciplineDao.findAll();
    }

    @Override
    public List<Artist> searchArtists(String query, String disciplineName, String city) {
        List<Artist> base = (city != null && !city.isEmpty())
                ? artistDao.findByCity(city)
                : artistDao.findAll();
        return base.stream()
                .filter(a -> query == null || query.isEmpty()
                        || a.getName().toLowerCase().contains(query.toLowerCase()))
                .filter(a -> disciplineName == null || disciplineName.isEmpty()
                        || a.getDisciplines().stream().anyMatch(d -> disciplineName.equals(d.getName())))
                .collect(Collectors.toList());
    }
}
