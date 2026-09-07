# Pillulier — conception

Date : 2026-09-07
Statut : validé, prêt pour le plan d'implémentation

## 1. Objectif

Application Android qui remplit deux rôles pour un utilisateur unique gérant ses
propres médicaments :

1. **Ne pas oublier de prendre** — rappels aux heures prévues, validation des prises.
2. **Ne pas tomber en rupture** — suivi du stock et alerte de renouvellement
   anticipée, exprimée en jours de traitement restants.

Les deux se nourrissent mutuellement : chaque prise validée décrémente le stock,
et le stock projeté sur la posologie donne la date d'alerte.

## 2. Périmètre

Dans le périmètre :

- un seul utilisateur, un seul profil, aucun compte, aucune synchronisation ;
- données entièrement locales ;
- saisie manuelle des médicaments ;
- posologies complètes : moments fixes, jours spécifiques, cures datées, doses
  décimales, médicaments à la demande ;
- rappels par notification avec relance, et alarme plein écran pour les
  médicaments critiques ;
- stock en unités dénombrables, alerte de renouvellement.

Hors périmètre :

- multi-profils, rôle aidant, synchronisation entre appareils ;
- scan de code-barres et base de données de médicaments (CIP13, base publique) ;
- écran d'historique dédié et export pour le médecin ;
- rattrapage rétroactif d'une prise oubliée ;
- formes liquides (gouttes, ml), inhalateurs, patchs ;
- jours fériés d'Alsace-Moselle et d'outre-mer.

## 3. Décisions produit

| Sujet | Décision |
|---|---|
| Utilisateurs | Un seul, local, pas de compte |
| Saisie des médicaments | Manuelle intégralement |
| Formes gérées | Dénombrables : comprimé, gélule, sachet, injection/ampoule |
| Heures de prise | Quatre moments globaux configurables : Matin, Midi, Soir, Coucher |
| Posologie | Rythme (tous les jours / jours de semaine / un jour sur N), dates de début et de fin, dose décimale par moment |
| À la demande | Pas de planning, pas de rappel, prise enregistrable à tout moment |
| Rappel | Une notification **par médicament**, actions *Pris* et *Plus tard* |
| Relance | Toutes les 15 minutes par défaut, par médicament, jusqu'à la clôture de la journée |
| Médicament critique | Alarme plein écran avec son, par-dessus l'écran verrouillé |
| Prise non cochée | Bascule en « oubliée » à la clôture de la journée ; le stock n'est pas décrémenté ; pas de correction rétroactive |
| Alimentation du stock | Deux gestes : *+1 boîte* et correction manuelle en unités |
| Alerte de renouvellement (planifié) | Seuil en jours de traitement restants, 7 jours par défaut |
| Marge dimanche/férié | Alerte reculée d'un jour de plus si un dimanche ou un férié tombe dans la fenêtre d'alerte |
| Alerte de renouvellement (à la demande) | Seuil en unités restantes, saisi par médicament |
| Écrans | Aujourd'hui, Semaine, Mes médicaments, Stock, Préférences |
| Vue semaine | Lecture seule |
| Réglages exposés | Heures des quatre moments, délai *Plus tard*, intervalle de relance, seuil d'alerte par défaut |

## 4. Architecture

Deux modules Gradle.

**`:domain`** — Kotlin pur, aucune dépendance Android. Contient les types métier
et le moteur de règles, sous forme de fonctions pures avec l'horloge injectée.
Tout ce qui porte du risque de calcul vit ici et se teste en JVM.

**`:app`** — Compose, Room, alarmes, notifications, injection Hilt. Activité
unique avec Navigation Compose, un ViewModel par écran exposant son état en
`StateFlow`, des dépôts au-dessus des DAO, et les cas d'usage qui appellent le
moteur. Seule exception à l'activité unique : l'activité d'alarme plein écran.

Le sens de dépendance est unique : `:app` dépend de `:domain`, jamais l'inverse.

## 5. Modèle de données

### 5.1 Types métier (`:domain`)

- `Forme` : `COMPRIME`, `GELULE`, `SACHET`, `INJECTION`
- `Moment` : `MATIN`, `MIDI`, `SOIR`, `COUCHER`
- `TypeOrdonnance` : `PLANIFIEE`, `A_LA_DEMANDE`
- `StatutPrise` : `A_VENIR`, `EN_RETARD`, `PRISE`, `OUBLIEE`
- `Rythme` (scellé) : `TousLesJours`, `JoursDeSemaine(jours: Set<DayOfWeek>)`,
  `UnJourSurN(n: Int)`

### 5.2 Tables Room

**`medicament`**

| Champ | Type | Note |
|---|---|---|
| `id` | Long | clé primaire auto-générée |
| `nom` | String | |
| `dosage` | String | texte libre, ex. « 500 mg » |
| `forme` | Forme | |
| `unitesParBoite` | Int | sert au geste *+1 boîte* |
| `stockUnites` | Double | stock courant, décimal pour les demi-unités |
| `seuilAlerteJours` | Int? | ordonnance planifiée ; nul = valeur par défaut des préférences |
| `seuilAlerteUnites` | Int? | ordonnance à la demande |
| `critique` | Boolean | déclenche l'alarme plein écran |

**`ordonnance`** — relation un-pour-un avec `medicament` (index unique sur
`medicamentId`) : un médicament porte au plus une ordonnance active.

| Champ | Type | Note |
|---|---|---|
| `id` | Long | clé primaire auto-générée |
| `medicamentId` | Long | clé étrangère, suppression en cascade, index unique |
| `type` | TypeOrdonnance | |
| `rythmeType` | String | discriminant du `Rythme` |
| `rythmeJours` | String? | jours de semaine sérialisés, pour `JoursDeSemaine` |
| `rythmeN` | Int? | pour `UnJourSurN` |
| `dateDebut` | LocalDate | ancre du calcul « un jour sur N » |
| `dateFin` | LocalDate? | nul = traitement au long cours |

**`dose_prescrite`** — les doses d'une ordonnance planifiée, une ligne par moment
concerné. Contrainte d'unicité sur `(ordonnanceId, moment)`.

| Champ | Type |
|---|---|
| `id` | Long |
| `ordonnanceId` | Long (clé étrangère, cascade) |
| `moment` | Moment |
| `dose` | Double |

**`moment_config`** — quatre lignes, l'heure de chaque moment.

| Champ | Type |
|---|---|
| `moment` | Moment (clé primaire) |
| `heure` | LocalTime |

**`evenement_prise`** — le journal du réel. Une ligne **uniquement** quand une
prise a eu lieu. Rien n'est écrit pour un oubli : son absence est l'oubli.
Contrainte d'unicité sur `(medicamentId, date, moment)` pour les prises
planifiées, ce qui rend l'enregistrement idempotent et empêche une double
décrémentation du stock. Les prises à la demande ont `moment` nul et ne sont donc
pas soumises à cette contrainte.

| Champ | Type | Note |
|---|---|---|
| `id` | Long | |
| `medicamentId` | Long | clé étrangère, cascade |
| `date` | LocalDate | jour de la prise |
| `moment` | Moment? | nul pour une prise à la demande |
| `doseReelle` | Double | |
| `enregistreLe` | Instant | horodatage de l'enregistrement |

### 5.3 Préférences

Dans DataStore, pas dans Room : `delaiPlusTardMinutes` (15),
`intervalleRelanceMinutes` (15), `seuilAlerteJoursDefaut` (7).

### 5.4 Convertisseurs

`LocalDate` en jour epoch, `LocalTime` en minutes depuis minuit, `Instant` en
millisecondes epoch, énumérations par leur nom, `Set<DayOfWeek>` en liste
séparée par des virgules.

### 5.5 Stock

`stockUnites` est modifié transactionnellement, avec l'écriture qui le motive :

- enregistrement d'une prise : décrémenté de `doseReelle`, dans la même
  transaction que l'insertion de l'événement ;
- *+1 boîte* : incrémenté de `unitesParBoite` ;
- correction manuelle : écrasé par la valeur saisie.

Pas de table de mouvements : mono-utilisateur, aucun besoin d'audit.

## 6. Moteur de règles (`:domain`)

Fonctions pures, sans état, horloge et calendrier passés en paramètre.

### 6.1 Prises attendues d'une date

```kotlin
fun prisesAttendues(
    date: LocalDate,
    ordonnances: List<OrdonnanceAvecDoses>,
    heures: Map<Moment, LocalTime>,
): List<PriseAttendue>

data class PriseAttendue(
    val medicamentId: Long,
    val moment: Moment,
    val dose: Double,
    val heure: LocalDateTime,
)
```

Une ordonnance retient une date si elle est de type `PLANIFIEE`, si la date est
dans sa fenêtre (`dateDebut <= date` et `dateFin` nulle ou `date <= dateFin`), et
si le rythme la retient :

- `TousLesJours` : toujours ;
- `JoursDeSemaine` : `date.dayOfWeek` appartient à l'ensemble ;
- `UnJourSurN` : `(date.toEpochDay() - dateDebut.toEpochDay()) % n == 0`

Chaque `DosePrescrite` de l'ordonnance retenue produit alors une prise attendue,
horodatée depuis `heures`. C'est l'unique chemin de calcul du planning : Aujourd'hui,
la vue semaine et la programmation des alarmes l'utilisent tous les trois, ce qui
rend une divergence entre écrans impossible par construction.

### 6.2 Statut d'une prise attendue

```kotlin
fun statut(
    prise: PriseAttendue,
    evenements: List<EvenementPrise>,
    maintenant: LocalDateTime,
): StatutPrise
```

- `PRISE` : un événement existe pour ce médicament, cette date et ce moment ;
- `OUBLIEE` : aucun événement et la journée de la prise est terminée
  (`prise.heure.toLocalDate() < maintenant.toLocalDate()`) ;
- `EN_RETARD` : aucun événement, journée en cours, `prise.heure <= maintenant` ;
- `A_VENIR` : sinon.

Aucun statut n'est persisté.

### 6.3 Projection du stock

```kotlin
fun projectionStock(
    stockUnites: Double,
    ordonnance: OrdonnanceAvecDoses,
    depuis: LocalDate,
    horizonJours: Int = 365,
): LocalDate?
```

On avance jour par jour depuis `depuis`, en retranchant la consommation prévue de
chaque journée (somme des doses des moments retenus ce jour-là). Le premier jour
dont la consommation ne peut plus être servie est la **date d'épuisement**.

L'itération plutôt qu'une division est nécessaire : un rythme « un jour sur deux »
ou une cure qui s'arrête donnent une consommation irrégulière.

Retourne `null` si le stock survit à l'horizon, ou si l'ordonnance a une
`dateFin` que le stock dépasse : il n'y a alors pas de rupture à annoncer.
Les ordonnances à la demande ne passent pas par cette fonction.

### 6.4 Date d'alerte

```kotlin
fun dateAlerte(
    dateEpuisement: LocalDate,
    seuilJours: Int,
    estFerie: (LocalDate) -> Boolean,
): LocalDate
```

Candidat = `dateEpuisement.minusDays(seuilJours)`. Si un dimanche ou un jour férié
tombe dans la fenêtre inclusive `[candidat, dateEpuisement]`, on recule le candidat
d'un jour supplémentaire — la pharmacie étant fermée, un jour de la fenêtre ne
permet pas d'acheter. Un seul jour de marge est ajouté, quel que soit le nombre de
jours fermés dans la fenêtre.

### 6.5 Jours fériés

```kotlin
object JoursFeriesFrance {
    fun estFerie(date: LocalDate): Boolean
    fun paques(annee: Int): LocalDate
}
```

Calculés, jamais stockés, sans accès réseau. Huit dates fixes : 1er janvier,
1er mai, 8 mai, 14 juillet, 15 août, 1er novembre, 11 novembre, 25 décembre.
Trois dates mobiles dérivées de Pâques, calculée par l'algorithme de Meeus
(comput grégorien) : lundi de Pâques (Pâques + 1), Ascension (Pâques + 39),
lundi de Pentecôte (Pâques + 50). France métropolitaine uniquement.

## 7. Rappels, alarmes et clôture de journée

### 7.1 Notifications

Une notification **par médicament**, pas par moment : à 8h avec trois médicaments
au Matin, trois notifications distinctes. Elles sont rassemblées sous une
notification de groupe « Matin — 3 médicaments » pour garder le volet lisible,
mais chacune se traite indépendamment, avec sa propre relance. C'est ce qui permet
de valider les comprimés et de laisser la piqûre en attente.

Actions de chaque notification :

- *Pris* — un receveur enregistre l'événement de prise et décrémente le stock
  dans la même transaction, puis annule la notification et sa relance ;
- *Plus tard* — reprogramme la notification du délai configuré (15 minutes par
  défaut).

Un appui sur le corps ouvre l'écran Aujourd'hui.

Deux canaux de notification : *Rappels* en importance haute, et *Critiques* en
importance haute avec intention plein écran.

### 7.2 Relance

Par médicament, indépendamment des autres, à l'intervalle configuré, tant que la
prise n'a pas eu de réponse. La boucle s'arrête à la clôture de la journée : aucune
relance ne survit au lendemain.

### 7.3 Médicament critique

Le rappel d'un médicament marqué critique passe par le canal *Critiques* et porte
une intention plein écran : au lieu d'une notification dans le volet, il affiche
une **activité d'alarme** par-dessus l'écran verrouillé, avec son, jusqu'à
réponse. Cette activité propose les mêmes deux réponses, *Pris* et *Plus tard*.
Deux médicaments du même moment peuvent donc se comporter différemment.

### 7.4 Programmation

Les alarmes sont **dérivées** du moteur et ne constituent jamais une vérité en soi ;
elles sont reconstructibles à tout moment.

- Une alarme exacte par couple médicament × moment retenu par le moteur, armée
  avec `setExactAndAllowWhileIdle`, sur une fenêtre glissante de trois jours — de
  l'ordre de quelques dizaines d'alarmes. Les ordonnances à la demande n'en
  produisent aucune.
- Un travail `WorkManager` quotidien à 00h05 : clôture la veille, puis réarme la
  fenêtre.
- Toute modification d'ordonnance, d'heure de moment ou de médicament réarme la
  fenêtre immédiatement.
- Un receveur de démarrage refait la même reconstruction après un redémarrage du
  téléphone.

### 7.5 Clôture de journée

La clôture annule les relances et retire les notifications de la veille. **Elle
n'écrit rien en base** : une prise non cochée devient « oubliée » par la seule
absence d'événement, dérivée à l'affichage.

### 7.6 Autorisations

Demandées dans cet ordre au premier lancement :

1. `POST_NOTIFICATIONS`
2. `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
3. `USE_FULL_SCREEN_INTENT`
4. une invitation à exclure l'app de l'optimisation de batterie
   (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — sans quoi aucune app de rappel de
   médicaments n'est fiable sur les surcouches constructeur

Plus `RECEIVE_BOOT_COMPLETED` pour le réarmement après redémarrage.

## 8. Écrans

Barre de navigation basse pour les quatre écrans principaux ; les préférences sont
accessibles par une icône de la barre du haut.

**Aujourd'hui** — les prises du jour groupées par moment, dans l'ordre
chronologique, chacune avec son statut et sa case à cocher. Un bouton distinct
enregistre une prise à la demande, hors planning. Une bannière en tête d'écran
signale les médicaments qui ont atteint leur date d'alerte de renouvellement.

**Semaine** — grille 7 jours × 4 moments, façon pillulier. Les jours passés
montrent ce qui a été pris ou oublié, les jours à venir ce qui est prévu.
Lecture seule.

**Mes médicaments** — la liste, puis un écran d'édition regroupant l'identité du
médicament (nom, dosage, forme, unités par boîte, critique, seuils) et son
ordonnance (type, rythme, dates de début et de fin, dose par moment).

**Stock** — par médicament : unités restantes, jours restants, date d'épuisement et
date d'alerte calculées, et deux gestes : *+1 boîte* et correction manuelle en
unités. Les médicaments à la demande forment une section à part, avec leur seuil
en unités.

**Préférences** — heures des quatre moments, délai du *Plus tard*, intervalle de
relance, seuil d'alerte par défaut.

## 9. Stack technique

- Gradle 9.3.1, AGP 8.12.0, Kotlin 2.1.20
- `compileSdk` 36, `targetSdk` 36, `minSdk` 26 (les canaux de notification en
  imposent 26 au minimum)
- Toolchain Java 17 pour la compilation ; Gradle exécuté sur le JBR d'Android
  Studio (Java 25)
- Jetpack Compose, Navigation Compose, Room, WorkManager, DataStore, Hilt

## 10. Stratégie de test

Développement en TDD : test d'abord, à chaque étape.

L'essentiel du risque est dans le moteur, donc l'essentiel des tests l'est aussi.

**`:domain`, tests JVM purs :**

- rythmes et bornes de dates : tous les jours, jours de semaine, un jour sur N
  avec son ancre, premier et dernier jour d'une cure, ordonnance sans date de fin ;
- dérivation des statuts aux quatre valeurs, dont la bascule à l'heure du moment
  et au changement de journée ;
- projection du stock : consommation régulière, rythme irrégulier, cure qui
  s'arrête avant l'épuisement, stock insuffisant pour une journée complète,
  horizon atteint ;
- date d'alerte : sans jour fermé dans la fenêtre, avec un dimanche, avec un férié,
  avec plusieurs jours fermés (un seul jour de marge) ;
- jours fériés : les onze jours vérifiés contre des dates connues sur plusieurs
  années, dont une année bissextile et une année où Pâques est précoce.

**`:app` :**

- DAO Room en tests instrumentés, dont l'idempotence de l'enregistrement d'une
  prise et la transaction prise + décrémentation du stock ;
- armement des alarmes et clôture de journée derrière une interface, testables avec
  une horloge et un programmateur simulés.
