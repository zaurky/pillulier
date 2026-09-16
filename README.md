# Pillulier

Application Android de gestion d'un pillulier de médicaments. Elle rappelle les prises
aux bonnes heures et prévient avant la rupture de stock, pour un utilisateur unique,
entièrement en local.

Pas de compte, pas de synchronisation, pas de réseau. Aucune permission Internet, et la
sauvegarde automatique Android est désactivée : les données de santé ne quittent pas
l'appareil.

## Ce que fait l'application

**Ne pas oublier de prendre.** Chaque médicament a sa propre notification à l'heure de
la prise, avec deux actions — *Pris* et *Plus tard* — et sa propre relance toutes les
15 minutes jusqu'à réponse. Une notification par médicament, pas une par moment : si le
matin comporte des comprimés et une piqûre, les deux ne se font pas au même instant et
chacune doit pouvoir être traitée séparément.

Un médicament marqué **critique** ne se contente pas d'une ligne dans le volet de
notifications : il ouvre une alarme plein écran par-dessus l'écran verrouillé, avec le
son d'alarme, jusqu'à réponse.

**Ne pas tomber en rupture.** L'alerte de renouvellement s'exprime en *jours de
traitement restants*, pas en nombre de comprimés — et elle est avancée d'un jour
supplémentaire si un dimanche ou un jour férié tombe dans la fenêtre, puisque la
pharmacie est fermée ce jour-là. Un seul jour de marge, quel que soit le nombre de
jours fermés.

### Cinq écrans

| Écran | Contenu |
|---|---|
| **Aujourd'hui** | les prises du jour groupées par moment, avec leur statut et une case à cocher, plus une bannière pour les médicaments à renouveler |
| **Semaine** | la grille 7 jours × 4 moments, façon pillulier physique. Lecture seule |
| **Mes médicaments** | la liste, et l'écran d'édition : identité du médicament et ordonnance |
| **Stock** | unités restantes, jours restants, dates d'épuisement et d'alerte, `+1 boîte` et correction manuelle |
| **Préférences** | heures des quatre moments, délai du *Plus tard*, intervalle de relance, seuil d'alerte par défaut |

### Le widget

Un widget d'écran d'accueil liste les prises qu'il reste à faire aujourd'hui — en
retard et à venir — et les rend cochables sans ouvrir l'application. Cocher enregistre
la prise comme l'action *Pris* d'une notification : même transaction, même décrément de
stock, même annulation d'alarme. Pendant dix secondes la ligne reste barrée avec un lien
*Annuler*, qui efface la prise, rend le stock et fait revenir le rappel.

Le widget lit `ObserverJournee`, donc le même `prisesAttendues` que les écrans : il ne
peut pas afficher autre chose qu'eux.

### Posologies gérées

Quatre moments globaux configurables (Matin, Midi, Soir, Coucher). Un rythme au choix :
tous les jours, certains jours de la semaine, ou un jour sur N. Une date de début et
une date de fin optionnelle, ce qui couvre aussi bien un traitement au long cours qu'une
cure d'antibiotique. Des doses décimales, donc le demi-comprimé. Et des médicaments
« à la demande », sans planning ni rappel, qui alertent sur un seuil en unités puisque
leur consommation n'est pas prévisible.

## L'idée qui structure le code

**La base de données tient un journal de ce qui s'est réellement passé, pas un ensemble
de statuts.** Une ligne d'`evenement_prise` n'existe que si la dose a vraiment été
prise ; une prise manquée n'écrit rien du tout, et c'est son absence qui *est* l'oubli.
Les quatre statuts — à venir, en retard, prise, oubliée — sont dérivés de ce journal et
de l'horloge, jamais stockés. Rien ne peut donc dériver.

**Les alarmes suivent la même règle.** Rien de ce qui est programmé n'est mémorisé :
l'identifiant d'un rappel est *calculé* à partir du triplet (médicament, jour, moment),
donc n'importe quelle partie de l'application peut annuler ou reposter une alarme sans
avoir rien retenu. C'est ce qui rend le réarmement après redémarrage, après mise à jour
ou à la clôture quotidienne à la fois trivial et fiable.

## Architecture

Deux modules Gradle. La dépendance va de `:app` vers `:domain`, jamais l'inverse.

### `:domain` — Kotlin pur, aucune dépendance Android

Le moteur de règles, en fonctions sans état avec l'horloge injectée. Il se teste en JVM
en quelques millisecondes, sans émulateur.

| Fichier | Rôle |
|---|---|
| `Types.kt` | énumérations, rythmes, entités métier immuables |
| `JoursFeriesFrance.kt` | Pâques par l'algorithme de Meeus, et les onze jours fériés métropolitains — calculés, jamais stockés |
| `Planning.kt` | `estJourActif` et `prisesAttendues` : **l'unique chemin de calcul du planning** |
| `Statut.kt` | dérivation des quatre statuts depuis le journal |
| `Stock.kt` | consommation, projection jour par jour, date d'alerte avec la marge dimanche/férié |
| `Rappel.kt` | la clé de rappel et son code de requête déterministe |

`prisesAttendues` est appelée par l'écran Aujourd'hui, la grille semaine *et* la
programmation des alarmes. Une divergence entre ce qu'affiche un écran et ce que
programme une alarme est donc impossible par construction.

La projection de stock avance jour par jour plutôt que de diviser : un rythme « un jour
sur deux » ou une cure qui s'arrête donnent une consommation qu'une division ne capture
pas.

### `:app` — Android

- `data/db/` — cinq tables Room, les DAO, les convertisseurs, l'amorçage des quatre moments
- `data/` — les mappeurs entités ↔ domaine, cinq dépôts, les préférences DataStore
- `usecase/` — les écritures (prise, stock, médicament) et les lectures observables
- `rappels/` — le programmateur d'alarmes exactes, les receveurs, les notifications, l'alarme plein écran, le travail quotidien
- `ui/` — un sous-paquet par écran, chacun avec son ViewModel et son état en `StateFlow`
- `widget/` — le widget Glance, ses deux actions et son point de rafraîchissement
- `temps/` — l'abstraction d'horloge qui rend tout le reste testable

Deux index uniques portent des garanties : celui sur `ordonnance(medicamentId)` impose
une ordonnance par médicament, et celui sur `evenement_prise(medicamentId, date, moment)`
rend l'enregistrement d'une prise planifiée idempotent — un double appui sur l'action
d'une notification ne peut pas décrémenter le stock deux fois. Comme SQLite considère
deux `NULL` comme distincts, les prises à la demande échappent à cette contrainte et
peuvent légitimement se répéter dans la journée.

## Construire et tester

```bash
./gradlew :domain:test :app:testDebugUnitTest    # 161 tests, en JVM
./gradlew :app:assembleDebug                     # construire l'APK
./gradlew :app:installDebug                      # installer sur l'appareil branché
```

Les tests du module `:app` tournent sous **Robolectric**, donc toute la suite s'exécute
en JVM sans émulateur ni appareil. C'était un choix délibéré : la boucle de
développement reste rapide.

| Module | Tests |
|---|---|
| `:domain` | 50 |
| `:app` | 111 |

## Stack

Gradle 9.3.1 · AGP 8.12.0 · Kotlin 2.1.20 · `compileSdk` et `targetSdk` 36 · `minSdk` 26

Jetpack Compose, Navigation Compose, Room (via KSP), WorkManager, DataStore, Hilt.

`minSdk` 26 rend `java.time` disponible nativement, donc `coreLibraryDesugaring` n'est
pas activé. L'application est en Compose de bout en bout : elle ne dépend pas de
`com.google.android.material`, et son thème XML (`Theme.Pillulier`) ne sert qu'à peindre
la fenêtre avant le premier frame.

Le code métier et l'interface sont intégralement en français — noms de classes, de
fonctions, de tables et de colonnes compris.

## Autorisations

Quatre autorisations sont demandées au premier lancement, dans cet ordre :

1. **Notifications** — sans quoi aucun rappel n'apparaît
2. **Alarmes exactes** — pour que le rappel tombe à l'heure et pas « dans l'heure »
3. **Notification plein écran** — pour l'alarme des médicaments critiques
4. **Exemption d'optimisation de batterie**

La quatrième est celle qu'on a tendance à sauter, et c'est celle qui casse tout en
silence sur les surcouches constructeur. Elle est présentée comme une étape numérotée
plutôt que cachée dans un réglage.

## Limites connues

- **Pas de rafraîchissement périodique des écrans.** Les statuts se recalculent au
  retour dans l'application, mais restent figés si elle est laissée ouverte à l'écran,
  et la date du jour ne bascule pas à minuit sur une application restée ouverte. Le
  rappel primaire reste la notification, pas l'écran.
- **Le champ stock de l'écran d'édition peut annuler une décrémentation concurrente.**
  Si une prise est enregistrée entre l'ouverture de la fiche et son enregistrement, la
  valeur d'avant est réécrite. La fenêtre est étroite et l'application est
  mono-utilisateur.
- **Une seule migration Room possible.** Le schéma est en version 1, exporté dans
  `app/schemas/`, sans `fallbackToDestructiveMigration` — délibérément, pour que le
  premier changement de schéma s'accompagne d'une vraie migration au lieu d'effacer le
  journal de l'utilisateur.
- La saisie est manuelle : pas de scan de code-barres ni de base de médicaments.
- Pas d'écran d'historique ni d'export pour le médecin.
- Pas de rattrapage rétroactif : une prise oubliée hier reste oubliée.
- Jours fériés de France métropolitaine uniquement — ni Alsace-Moselle, ni outre-mer.

## Documentation

- `docs/superpowers/specs/2026-09-07-pillulier-design.md` — la conception validée, avec
  les décisions produit et leurs raisons
- `docs/superpowers/plans/2026-09-07-pillulier.md` — le plan d'implémentation en 19
  tâches dont le code est issu
