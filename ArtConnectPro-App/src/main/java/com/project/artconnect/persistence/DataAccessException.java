package com.project.artconnect.persistence;

/**
 * Exception non checkee encapsulant les erreurs JDBC.
 * 
 * Permet a la couche DAO de propager les SQLException sans contaminer
 * la signature des methodes des interfaces DAO et services.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
