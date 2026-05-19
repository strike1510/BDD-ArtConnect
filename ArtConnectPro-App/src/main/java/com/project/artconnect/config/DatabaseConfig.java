package com.project.artconnect.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration de la base de donnees.
 * Charge les parametres depuis le fichier application.properties (classpath).
 * Les valeurs par defaut sont utilisees si le fichier est absent.
 */
public class DatabaseConfig {

    public static final String URL;
    public static final String USER;
    public static final String PASSWORD;
    public static final String APP_MODE;

    private static final String CONFIG_FILE = "/application.properties";

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[DatabaseConfig] application.properties introuvable, utilisation des valeurs par defaut.");
            }
        } catch (IOException e) {
            System.err.println("[DatabaseConfig] Erreur de lecture de application.properties : " + e.getMessage());
        }

        URL = props.getProperty("db.url",
                "jdbc:mysql://localhost:3306/artconnect_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        USER = props.getProperty("db.user", "root");
        PASSWORD = props.getProperty("db.password", "password");
        APP_MODE = props.getProperty("app.mode", "jdbc");
    }

    private DatabaseConfig() {
        // Classe utilitaire : pas d'instanciation
    }
}
