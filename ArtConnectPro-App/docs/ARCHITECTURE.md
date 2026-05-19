# Architecture de l'application ArtConnect (Etape 4)

## Vue en couches

```
+------------------------------------------------------------+
|                    PRESENTATION (UI)                       |
|  JavaFX FXML + Controllers (ArtistController, etc.)        |
|         Aucune dependance directe a la BDD                 |
+----------------------------+-------------------------------+
                             |  ServiceProvider
                             v
+------------------------------------------------------------+
|                        SERVICE                             |
|   Interfaces : ArtistService, ArtworkService, ...          |
|   Impl 1 : In-Memory (donnees factices, demo / tests)      |
|   Impl 2 : JDBC      (delegue aux DAO, mode production)    |
+----------------------------+-------------------------------+
                             |
                             v
+------------------------------------------------------------+
|                          DAO                               |
|   Interfaces : ArtistDao, ArtworkDao, GalleryDao, ...      |
|   Implementations JDBC (PreparedStatement + transactions)  |
|   IdentityRegistry : cache objet Java -> id BDD            |
|   DataAccessException : encapsule SQLException             |
+----------------------------+-------------------------------+
                             |
                             v
+------------------------------------------------------------+
|                     CONNEXION JDBC                         |
|       ConnectionManager + DatabaseConfig (.properties)     |
+----------------------------+-------------------------------+
                             |
                             v
+------------------------------------------------------------+
|             MySQL  --  base artconnect_db                  |
|  Tables, vues, triggers, procedures, fonctions, indices    |
+------------------------------------------------------------+
```

## Choix d'implementation

### Le defi "OOP-first" du squelette

Le squelette fourni respecte strictement l'orientation objet : aucune classe
du modele (`Artist`, `Artwork`, `Gallery`...) ne possede de champ `id`. Or la
base relationnelle a besoin de cles primaires pour identifier chaque ligne.

Pour reconcilier ces deux mondes sans modifier les modeles, on introduit
`IdentityRegistry` : une `IdentityHashMap` qui associe chaque reference
d'objet Java a son id BDD. La cache est alimentee par les DAO :

- au moment du `findAll()` ou `findByXxx()`, on memorise `(objet, id)` ;
- au moment du `save()`, on memorise l'id genere par `RETURN_GENERATED_KEYS` ;
- pour les `update`/`delete`/FK, on retrouve l'id soit via la cache, soit en
  faisant un SELECT par attribut unique (name, email, title).

### Mode dual : JDBC vs In-Memory

Le `ServiceProvider` choisit a l'init quelle famille de services exposer en
fonction de la propriete `app.mode` (fichier `application.properties`) :

```
app.mode=jdbc     -> services JDBC -> DAO -> MySQL
app.mode=memory   -> services InMemory (donnees factices)
```

C'est utile pour :
- demos rapides sans MySQL installe (mode memory) ;
- developpement / tests des controllers UI sans dependre de la BDD ;
- comparaison comportementale entre les deux modes.

L'UI est **strictement identique** dans les deux modes : aucun controller
n'a ete modifie pour l'etape 4.

### Gestion des transactions

Trois cas typiques sont geres explicitement :

1. **`JdbcArtistDao.save/update`** : INSERT dans `artist` + INSERT batch dans
   `artist_discipline`. La connexion est passee en `autoCommit(false)`,
   `commit()` ou `rollback()` selon le succes.
2. **`JdbcBookingDao.save`** : INSERT dans `booking`. La verification de
   capacite est deleguee au trigger SQL `tr_check_workshop_availability` ;
   en cas d'echec la transaction est annulee et une `DataAccessException`
   remonte.
3. **`Connection` toujours fermee** dans un bloc `finally` (try-with-resources
   pour les cas simples, methode utilitaire `close()` pour les transactions
   manuelles).

### Gestion des erreurs

Toutes les `SQLException` sont encapsulees dans `DataAccessException`
(RuntimeException), pour ne pas polluer la signature des interfaces
`ArtistService`, etc. Cette exception remonte jusqu'au controller, qui peut
choisir d'afficher un Alert utilisateur.

`MainApp` execute un `testConnection()` au demarrage en mode JDBC : si la BDD
n'est pas joignable, un Alert l'indique avant que l'UI ne tente d'acceder aux
donnees.

## Flux d'execution typique

### Affichage de la liste des artistes (mode JDBC)

```
ArtistController.initialize()
  -> ServiceProvider.getArtistService()         [JdbcArtistService]
    -> artistDao.findAll()                      [JdbcArtistDao]
      -> ConnectionManager.getConnection()      [DriverManager]
      -> PreparedStatement SELECT ... FROM artist
      -> mapRow() pour chaque ligne :
           - cree un Artist Java
           - charge ses Disciplines (SELECT join)
           - registre.register(Artist.class, a, id)
      -> ferme ResultSet, Statement, Connection
    <- List<Artist>
  <- List<Artist>
artistTable.setItems(...)
```

### Inscription a un atelier (mode JDBC)

```
WorkshopService.bookWorkshop(workshop, member)
  -> JdbcWorkshopService delegue a JdbcBookingDao.save(new Booking(...))
    -> setAutoCommit(false)
    -> resolveWorkshopId() / resolveMemberId() via cache ou SELECT
    -> INSERT INTO booking ...
       -> declenche le trigger tr_check_workshop_availability
       -> si capacite atteinte : SQLException
    -> commit() ou rollback()
  -> member.addBooking(b)   (mise a jour cote objet Java)
```

## Mapping objets <-> tables (synthese)

| Classe Java        | Table BDD          | Particularites                                     |
|--------------------|--------------------|----------------------------------------------------|
| Artist             | artist             | + jointure avec artist_discipline                  |
| Discipline         | discipline         | catalogue partage                                  |
| Artwork            | artwork            | FK artist_id, status ENUM                          |
| Gallery            | gallery            |                                                    |
| Exhibition         | exhibition         | FK gallery_id, dates verifiees par trigger         |
| Workshop           | workshop           | FK instructor_id (Artist), level ENUM minuscules   |
| CommunityMember    | community_member   | membership_type ENUM                               |
| Booking            | booking            | FK workshop_id + member_id, transaction + trigger  |
| Review             | review             | FK reviewer_id + artwork_id, rating CHECK 1..5     |

## Limitations connues / pistes d'evolution

- Les CRUD complets (create/update/delete) ne sont actuellement pas accessibles
  depuis l'UI : seuls les ecrans de consultation exploitent les services.
  L'etape 5 prevoit d'enrichir l'interface (boutons, formulaires).
- Pas de connection pool : chaque appel DAO ouvre/ferme sa connexion. Pour
  une vraie mise en production on integrerait HikariCP.
- L'`IdentityRegistry` est en memoire : il est reinitialise a chaque
  redemarrage de l'application.
