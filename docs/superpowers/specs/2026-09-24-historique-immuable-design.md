# Historique immuable — conception

Le planning d'un jour passé doit rester ce qu'il était ce jour-là. Aujourd'hui il est
recalculé depuis la prescription en vigueur au moment de l'affichage : changer un
rythme réécrit rétroactivement toute l'histoire du médicament.

## Le problème

Un médicament porte **une seule** ordonnance, mise à jour en place — `DepotOrdonnances`
appelle `mettreAJourOrdonnance`, puis efface et réinsère les doses. L'index sur
`medicamentId` est déclaré `unique = true`, ce qui interdit structurellement d'en avoir
plusieurs.

`prisesAttendues(date, ordonnances, heures)` applique cette unique ordonnance à toute
date, passée comme future. Le passé n'est donc pas enregistré : il est dérivé de la
prescription du jour.

Conséquence observée : passer une ordonnance de « tous les jours » à « un jour sur
deux » fait disparaître une prise passée sur deux de la vue semaine. Les prises
réellement enregistrées (`evenement_prise`) survivent — elles sont un journal du réel —
mais elles se retrouvent orphelines d'un planning qui ne les prévoit plus.

Un second vecteur abîme l'historique de la même façon : la clé étrangère de
`evenement_prise` vers `medicament` est en `CASCADE`. Supprimer un médicament efface
l'intégralité de ses prises passées.

### Ce qui n'est pas le problème

Les heures des moments sont globales et mutables, mais `statut()` ne consulte l'heure
attendue que pour un jour **courant ou futur** : pour un jour passé il ne regarde que
l'existence d'un événement. Changer l'heure du Matin ne modifie donc aucun statut
passé, seulement un ordre de tri invisible dans une vue groupée par moment. Hors
périmètre.

## Approche retenue : versionner les ordonnances

Un médicament porte N ordonnances disjointes dans le temps. Modifier une prescription
clôt la version en vigueur et en ouvre une nouvelle.

`estJourActif()` teste **déjà** la fenêtre `dateDebut`/`dateFin` de chaque ordonnance.
Dès lors que la liste contient plusieurs versions, `prisesAttendues()` sélectionne
naturellement la bonne à chaque date : sa signature ne change pas, et ses cinq
lecteurs — Aujourd'hui, Semaine, Stock, widget, réarmement — continuent de fonctionner
sans modification. L'invariant « unique chemin de calcul du planning » est préservé.

### Approches écartées

**Figer un journal du planning** à chaque clôture quotidienne, et lire le passé depuis
cette table. Écartée pour trois raisons : elle ne répare pas l'historique déjà
enregistré, puisqu'elle ne fige qu'à partir de sa mise en service ; un jour où
l'application n'a pas tourné apparaîtrait vide, rendant indiscernable un jour sans
traitement d'un jour dont on a perdu la trace — et effaçant les statuts `OUBLIEE` ;
enfin elle crée un second chemin de calcul du planning, deux sources à maintenir
d'accord, ce qui est précisément l'origine des divergences constatées.

**Un médicament par prescription.** Aucun code, mais le stock se scinde à chaque
changement de posologie et la liste se remplit de doublons.

## Modèle de données

### Ancrage du rythme

`Ordonnance` gagne `dateAncrage: LocalDate`. `dateDebut` devient la borne de *cette
version* ; `dateAncrage` reste l'origine du rythme, propagée d'une version à la
suivante. `estJourActif()` compte les jours de `Rythme.UnJourSurN` depuis `dateAncrage`
au lieu de `dateDebut`.

Sans cela, scinder une ordonnance décale la phase. Une prescription « un jour sur deux »
démarrée le 1er janvier prévoit les 1, 3, 5, 7. Créer une version le 4 janvier
donnerait 1, 3, **4**, 6, 8 : deux jours consécutifs, puis un calendrier inversé.

### Tables

| Table | Changement |
|---|---|
| `ordonnance` | l'index `medicamentId` perd `unique = true` ; colonne `dateAncrage` |
| `medicament` | colonne `archiveLe: Instant?` |
| `evenement_prise` | clé étrangère `CASCADE` → `RESTRICT` |

`RESTRICT` rend la survie de l'historique structurelle plutôt que conventionnelle :
aucun chemin de code, présent ou futur, ne peut l'effacer en silence. Une tentative de
suppression échoue bruyamment.

`dose_prescrite` garde sa cascade vers `ordonnance` : les doses d'une version morte
doivent mourir avec elle.

### Migration 1 → 2

La première migration du projet, celle que le schéma en version 1 sans
`fallbackToDestructiveMigration` réservait délibérément.

SQLite ne sait ni retirer un index unique ni modifier une clé étrangère en place :
`ordonnance` et `evenement_prise` sont recréées puis recopiées. `medicament.archiveLe`
est un simple `ALTER TABLE`. `dateAncrage` est initialisée à `dateDebut` pour les lignes
existantes.

Aucun rattrapage de données n'est nécessaire : l'ordonnance en base devient d'office la
version couvrant tout le passé, avec sa `dateDebut` d'origine.

## Écriture

### La règle des versions

`EnregistrerMedicament` reçoit une `dateEffet`. **La nouvelle version prend tout à
partir de cette date** :

- les versions antérieures sont clôturées à `dateEffet - 1` ;
- les versions commençant à `dateEffet` ou après sont supprimées.

Une règle unique, sans cas particulier, qui couvre la création, la modification du
jour, la correction rétroactive et la re-correction d'une correction.

La nouvelle version hérite de la `dateAncrage` de celle qu'elle remplace, sauf si
`dateEffet` la précède : l'ancrage devient alors `dateEffet`, puisque aucune version
plus ancienne ne subsiste pour porter l'origine du rythme.

La date est saisissable et pré-remplie à aujourd'hui. Réécrire le passé reste donc
possible **sur demande explicite** ; ce que le chantier supprime, c'est la réécriture
accidentelle, aujourd'hui comportement par défaut et invisible.

### Pas de version inutile

Si type, rythme, doses et date de fin sont identiques à la version en vigueur, aucune
version n'est créée. Renommer un médicament, corriger son stock ou le passer en critique
ne doit pas fabriquer une coupure dans son historique de prescription.

### Archivage

`SupprimerMedicament` devient `ArchiverMedicament`. Il pose `archiveLe`, clôt la version
d'ordonnance en cours à aujourd'hui, ferme la fenêtre d'alarmes et retire les
notifications. Tout le code existant est conservé ; seul l'appel à
`medicaments.supprimer(id)` disparaît.

Clore l'ordonnance suffit à faire sortir le médicament du planning futur —
`estJourActif()` renvoie faux au-delà de la date de fin — sans qu'aucun lecteur du
planning ait à connaître la notion d'archive.

### Le piège du surensemble

`ReArmerRappels` annule délibérément un surensemble bâti sur `medicaments.tous()` :
dériver les clés du planning courant laisserait vivre l'alarme d'une dose retirée de
l'ordonnance.

`tous()` doit donc continuer de renvoyer **tous** les médicaments, archivés compris.
L'exclusion des archivés appartient aux trois lectures qui s'adressent à
l'utilisateur : la liste « Mes médicaments », l'écran Stock et les alertes de
renouvellement.

## Lecture

Inchangés : `prisesAttendues()`, `ObserverJournee`, `ObserverSemaine`, `ReArmerRappels`,
le widget. Tous parcourent la liste complète des ordonnances et laissent
`estJourActif()` trancher par date.

Deux lecteurs, en revanche, supposent aujourd'hui **une** ordonnance par médicament et
casseraient en silence :

- `ObserverStock` indexe les ordonnances par `associateBy { medicamentId }`. Avec
  plusieurs versions, cette fonction en retient une arbitrairement — la dernière
  rencontrée — et projette le stock depuis une prescription qui peut être périmée. Il
  lui faut la version en vigueur aujourd'hui.
- `AujourdhuiViewModel` dérive l'ensemble des médicaments « à la demande » du type des
  ordonnances. Un médicament passé de planifié à la demande porterait deux versions de
  types différents et apparaîtrait dans les deux listes. Même correction : la version
  en vigueur aujourd'hui.

Ces deux-là sont le vrai coût caché du versionnement, et la raison pour laquelle
`enVigueur` doit exister avant tout changement d'écriture.

`DepotOrdonnances.pourMedicament(id)` devient `enVigueur(medicamentId, date)` — la
version active à une date donnée. Deux appelants : `EditionViewModel.charger()`, qui
affiche la prescription du jour, et `EnregistrerMedicament`, qui doit savoir laquelle
clôturer.

Quand aucune version ne couvre la date — un médicament archivé, dont la dernière
version s'est close hier — `enVigueur` rend la version la plus récente qui précède.
L'écran d'édition d'un médicament archivé montre ainsi sa dernière prescription connue
plutôt qu'un formulaire vide. Il ne rend `null` que pour un médicament sans aucune
ordonnance.

Le nom d'un médicament archivé reste disponible pour l'affichage du passé, puisque
`tous()` le renvoie toujours.

## Interface

L'écran d'édition gagne un sélecteur **« S'applique à partir du »**, pré-rempli à
aujourd'hui et visible uniquement en modification. À la création, `dateDebut` joue déjà
ce rôle et un second champ prêterait à confusion.

Le dialogue de suppression annonce aujourd'hui que les prises seront effacées — ce qui
cessera d'être vrai. Il devient « Archiver » et indique que l'historique est conservé.

## Tests

En TDD, dans cet ordre.

**Domaine.** La phase de `UnJourSurN` survit à une scission — le cas 1, 3, 4, 6 décrit
plus haut ne doit pas se produire. `prisesAttendues` sur deux versions qui se succèdent
rend la bonne dose de part et d'autre de la coupure.

**Migration.** `MigrationTestHelper` sur 1→2 : une ordonnance existante survit avec
`dateAncrage = dateDebut`, les événements de prise survivent, l'index unique a disparu.

**Écriture.** La règle de `dateEffet` sous ses quatre formes ; aucune version créée
quand seul le nom change.

**Archivage.** Les prises passées restent lisibles, le planning futur se ferme, la
fenêtre d'alarmes aussi.

**Non-régression.** Le bug d'origine, littéralement : une ordonnance quotidienne, des
prises cochées sur deux semaines, passage à « un jour sur deux », puis vérifier que pas
une seule prise passée n'a bougé.

## Hors périmètre

- Consulter les anciennes versions d'une prescription : elles existent en base et
  servent au calcul, mais rien ne les affiche.
- Figer les heures des moments (voir « Ce qui n'est pas le problème »).
- Désarchiver un médicament.
