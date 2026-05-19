package com.project.artconnect.dao;

import com.project.artconnect.model.Discipline;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object pour l'entite Discipline.
 */
public interface DisciplineDao {
    List<Discipline> findAll();

    Optional<Discipline> findByName(String name);

    void save(Discipline discipline);
}
