# ArtConnect Pro - Local Art Community Platform

Application JavaFX de gestion d'une communaute artistique locale. Connectee a
une base MySQL via JDBC.

## Sommaire

- [Pre-requis](#pre-requis)
- [Installation de la base](#installation-de-la-base)
- [Configuration](#configuration)
- [Lancement de l'application](#lancement-de-lapplication)
- [Architecture](#architecture)
- [Structure du projet](#structure-du-projet)

## Pre-requis

- Java 17 ou plus
- Maven 3.6+
- MySQL 8.0+ avec un utilisateur capable de creer une base

## Installation de la base

Depuis le dossier `sql/`, executez les scripts dans l'ordre :

```bash
mysql -u root -p < sql/01_schema.sql        # cree la base, les tables, vues, triggers, procedures
mysql -u root -p < sql/02_sample_data.sql   # insere les donnees d'exemple (5 artistes, 6 oeuvres, etc.)
mysql -u root -p < sql/03_transactions.sql  # (optionnel) joue les scenarios transactionnels de l'etape 3
```

Verification rapide :

```sql
USE artconnect_db;
SELECT COUNT(*) FROM artist;       -- doit renvoyer 5
SELECT COUNT(*) FROM artwork;      -- doit renvoyer 6
SELECT * FROM view_artwork_catalog; -- doit renvoyer le catalogue avec le nom de l'artiste
```

## Configuration

Editez `src/main/resources/application.properties` :

```properties
db.url=jdbc:mysql://localhost:3306/artconnect_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.user=root
db.password=VOTRE_MOT_DE_PASSE
app.mode=jdbc
```

Pour basculer en mode demo sans BDD : `app.mode=memory`.

## Lancement de l'application

```bash
mvn clean javafx:run
```

Au demarrage, l'app verifie la connexion MySQL en mode `jdbc` et affiche une
boite de dialogue d'erreur si la base est injoignable. Le titre de la fenetre
indique le mode actif (`[MySQL]` ou `[In-Memory]`).

## Architecture

Voir `docs/ARCHITECTURE.md` pour le detail. En resume :

```
JavaFX Controllers
       v
  ServiceProvider  --(lit app.mode)
       v
ArtistService, ArtworkService, ...   (interfaces)
       v
JdbcXxxService          OU       InMemoryXxxService
       v
JdbcXxxDao  (PreparedStatement)
       v
ConnectionManager + DriverManager
       v
            MySQL (artconnect_db)
```

Points cles :

- L'UI est strictement identique en mode JDBC et en mode memory.
- Le `IdentityRegistry` resoud le probleme "OOP-first" du squelette : les
  classes du modele n'ont pas de champ `id`, on tient un mapping
  `objet -> id BDD` en memoire.
- Toutes les ecritures multi-tables passent par une transaction
  (`autoCommit(false)` + commit/rollback).
- Le trigger `tr_check_workshop_availability` empeche les sur-reservations
  cote BDD ; le DAO encapsule l'erreur en `DataAccessException`.

## Structure du projet

```
ArtConnectPro-App/
|-- pom.xml
|-- README.md
|-- docs/
|   `-- ARCHITECTURE.md
|-- sql/
|   |-- 01_schema.sql
|   |-- 02_sample_data.sql
|   `-- 03_transactions.sql
`-- src/main/
    |-- resources/
    |   |-- application.properties
    |   `-- com/project/artconnect/ui/  (FXML)
    `-- java/com/project/artconnect/
        |-- MainApp.java                       point d'entree
        |-- config/DatabaseConfig.java         lit application.properties
        |-- util/
        |   |-- ConnectionManager.java         DriverManager
        |   `-- ServiceProvider.java           switch JDBC / memory
        |-- model/                             entites (sans id, OOP-first)
        |-- dao/                               interfaces DAO
        |-- persistence/                       impl JDBC + IdentityRegistry
        |-- service/                           interfaces Service
        |   `-- impl/                          InMemoryXxx + JdbcXxx
        `-- ui/                                JavaFX Controllers
```

## Tests / Demonstration

1. Lancer en mode `memory` (`app.mode=memory`) pour s'assurer que l'UI marche
   sans BDD.
2. Lancer en mode `jdbc` apres avoir installe le schema + les donnees.
3. Comparer les onglets Artists / Artworks / Galleries / Exhibitions /
   Workshops / Community : le contenu doit etre identique.
4. Onglet Discover : 3 expositions + 3 workshops sont affiches en cartes.

## Auteurs

Thouraud de Lavignere Hugo - Tea Julia - Toure Ines - Vimalan Kogulaan

Projet Bases de Donnees 2 (TI603) - Efrei Paris.
