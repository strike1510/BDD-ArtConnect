package com.project.artconnect.util;

import com.project.artconnect.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gestionnaire des connexions JDBC vers la base ArtConnect.
 * 
 * Chaque appel a getConnection() ouvre une nouvelle connexion ; il appartient
 * au DAO de la fermer (try-with-resources recommande).
 */
public class ConnectionManager {

    static {
        // Chargement explicite du driver MySQL (utile dans certains environnements).
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                    "Driver MySQL introuvable. Verifiez que mysql-connector-j est present dans le classpath.");
        }
    }

    private ConnectionManager() {
        // Classe utilitaire : pas d'instanciation
    }

    /**
     * Fournit une connexion vers la base de donnees MySQL.
     *
     * @return Une nouvelle connexion JDBC ouverte.
     * @throws SQLException si la connexion a la base echoue.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                DatabaseConfig.URL,
                DatabaseConfig.USER,
                DatabaseConfig.PASSWORD);
    }

    /**
     * Teste la connexion a la base. Renvoie true si la base est joignable.
     */
    public static boolean testConnection() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            System.err.println("[ConnectionManager] Echec du test de connexion : " + e.getMessage());
            return false;
        }
    }
}
