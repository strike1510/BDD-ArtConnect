package com.project.artconnect.persistence;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registre interne de mapping {objet Java -> identifiant BDD}.
 * 
 * Le projet ArtConnect a fait le choix "OOP-first" : aucun champ id n'est
 * present dans les classes du modele. Pour pouvoir retrouver l'id base d'un
 * objet (utile pour les UPDATE / DELETE / FK), on maintient une cache en
 * memoire alimentee a chaque findAll() ou save().
 * 
 * On utilise une IdentityHashMap car deux Artist peuvent avoir le meme nom
 * (theoriquement) mais sont distincts en memoire.
 * 
 * Cette classe est concue comme un singleton accessible par tous les DAO.
 */
public final class IdentityRegistry {

    private static final IdentityRegistry INSTANCE = new IdentityRegistry();

    public static IdentityRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<Class<?>, Map<Object, Integer>> registry = new IdentityHashMap<>();

    private IdentityRegistry() {
    }

    public synchronized <T> void register(Class<T> type, T object, int id) {
        registry.computeIfAbsent(type, k -> new IdentityHashMap<>()).put(object, id);
    }

    public synchronized <T> Integer getId(Class<T> type, T object) {
        Map<Object, Integer> map = registry.get(type);
        return map == null ? null : map.get(object);
    }

    public synchronized <T> void unregister(Class<T> type, T object) {
        Map<Object, Integer> map = registry.get(type);
        if (map != null) {
            map.remove(object);
        }
    }

    public synchronized void clearType(Class<?> type) {
        registry.remove(type);
    }

    public synchronized void clearAll() {
        registry.clear();
    }
}
