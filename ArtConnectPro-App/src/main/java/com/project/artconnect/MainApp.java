package com.project.artconnect;

import com.project.artconnect.config.DatabaseConfig;
import com.project.artconnect.util.ConnectionManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // En mode JDBC, on verifie la connexion avant de charger l'UI :
        // un crash franc avec un Alert est plus utile qu'une exception silencieuse
        // au moment ou un controller appelle un service.
        if ("jdbc".equalsIgnoreCase(DatabaseConfig.APP_MODE)) {
            if (!ConnectionManager.testConnection()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("ArtConnect - Erreur de connexion");
                alert.setHeaderText("Impossible de se connecter a MySQL");
                alert.setContentText(
                        "Verifiez que :\n" +
                        " - le serveur MySQL est demarre,\n" +
                        " - la base 'artconnect_db' existe (script 01_schema.sql),\n" +
                        " - les identifiants de application.properties sont corrects.\n\n" +
                        "URL : " + DatabaseConfig.URL + "\n" +
                        "User : " + DatabaseConfig.USER);
                alert.showAndWait();
                // On laisse quand meme l'app se lancer pour permettre au user de voir l'UI
                // ; les ecrans afficheront une exception lors du premier acces aux donnees.
            }
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/project/artconnect/ui/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        stage.setTitle("ArtConnect Pro - Local Art Community Platform ["
                + (DatabaseConfig.APP_MODE.equalsIgnoreCase("jdbc") ? "MySQL" : "In-Memory") + "]");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
