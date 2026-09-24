# Historique immuable — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** le planning d'un jour passé reste ce qu'il était ce jour-là, quelles que soient les modifications ultérieures de la prescription.

**Architecture:** un médicament porte N ordonnances disjointes dans le temps au lieu d'une seule mise à jour en place. `estJourActif()` teste déjà la fenêtre `dateDebut`/`dateFin` de chaque ordonnance, donc `prisesAttendues()` sélectionne naturellement la bonne version à chaque date, sans changer de signature. Une `dateAncrage` distincte de `dateDebut` préserve la phase des rythmes « un jour sur N » à travers les scissions.

**Tech Stack:** Kotlin 2.1.20, Room 2.7.0 (KSP), Hilt, Compose, Robolectric 4.14.1.

**Spec:** `docs/superpowers/specs/2026-09-24-historique-immuable-design.md`

## Global Constraints

- Gradle 9.3.1 · AGP 8.12.0 · Kotlin 2.1.20 · `compileSdk`/`targetSdk` 36 · `minSdk` 26
- Tout le code et toute l'interface sont **en français** : noms de classes, de fonctions, de variables, de tests, et libellés affichés
- Les noms de tests et les commentaires de code sont **sans accents** ; les libellés affichés à l'utilisateur en portent
- Tous les tests tournent **en JVM** (Robolectric), jamais sur émulateur. Les tests Android portent `@RunWith(AndroidJUnit4::class)` et `@Config(sdk = [34])`
- Commande de test : `./gradlew :app:testDebugUnitTest` ; suite complète : `./gradlew test`
- La base est en version 1 sans `fallbackToDestructiveMigration`, délibérément : **toute** évolution de schéma s'accompagne d'une vraie migration testée
- Messages de commit : `type(scope): description`, corps explicatif sans accents, terminés par
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

## Structure des fichiers

| Fichier | Responsabilité | Tâche |
|---|---|---|
| `domain/.../Types.kt` | `Ordonnance.dateAncrage` | 1 |
| `domain/.../Planning.kt` | `estJourActif` compte depuis l'ancrage | 1 |
| `app/.../db/Entites.kt` | index non unique, `dateAncrage`, `archiveLe`, FK `RESTRICT` | 1 |
| `app/.../db/Migrations.kt` | **créé** — `MIGRATION_1_2` | 1 |
| `app/.../db/PillulierDatabase.kt` | version 2 | 1 |
| `app/.../data/Mappeurs.kt` | mappe `dateAncrage` et `archiveLe` | 1 |
| `app/.../db/OrdonnanceDao.kt` | requêtes multi-versions | 2 |
| `app/.../data/DepotOrdonnances.kt` | `enVigueur`, `enregistrerVersion` | 2, 3 |
| `app/.../usecase/ObserverStock.kt` | version en vigueur au lieu d'`associateBy` | 2 |
| `app/.../ui/aujourdhui/AujourdhuiViewModel.kt` | idem pour « à la demande » | 2 |
| `app/.../usecase/EnregistrerMedicament.kt` | `dateEffet`, `ArchiverMedicament` | 3, 4 |
| `app/.../data/DepotMedicaments.kt` | `archiver`, `observerActifs` | 4 |
| `app/.../ui/medicaments/Edition*.kt` | sélecteur de date d'effet, dialogue « Archiver » | 5 |

---

### Task 1: Modèle versionné et migration 1→2

Le domaine, le schéma et la migration ne peuvent pas être livrés séparément : ajouter `dateAncrage` au domaine sans colonne pour la stocker ne compile pas utilement. Cette tâche ne change **aucun comportement** — elle prépare le terrain. À la fin, tous les appelants passent `dateAncrage = dateDebut` et l'application se comporte exactement comme avant.

**Files:**
- Modify: `domain/src/main/kotlin/fr/pillulier/domain/Types.kt`
- Modify: `domain/src/main/kotlin/fr/pillulier/domain/Planning.kt:19-20`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/Entites.kt:29-53`, `:16-27`, `:85-107`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/Migrations.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/PillulierDatabase.kt:17`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/Mappeurs.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/di/ModuleDonnees.kt:25`
- Modify: `app/build.gradle.kts:38`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/PlanningTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/data/db/MigrationTest.kt` (créé)

**Interfaces:**
- Produces: `Ordonnance(id, medicamentId, type, rythme, dateDebut, dateFin, dateAncrage)` — `dateAncrage: LocalDate`, sans valeur par défaut
- Produces: `Medicament(..., critique, archiveLe: Instant?)` — `archiveLe` par défaut `null`
- Produces: `MIGRATION_1_2: Migration` dans `fr.pillulier.app.data.db`

- [ ] **Step 1: Écrire le test de phase du rythme (celui qui échoue)**

Dans `domain/src/test/kotlin/fr/pillulier/domain/PlanningTest.kt`, ajouter. Le helper local `ordonnance(...)` existe déjà dans ce fichier — lui ajouter le paramètre `dateAncrage` à l'étape 3.

```kotlin
    @Test
    fun `un jour sur deux garde sa phase quand l ordonnance est scindee`() {
        // Prescription du 1er janvier, prises les 1, 3, 5, 7.
        // Scindee le 4 : la seconde version doit continuer sur les jours impairs.
        val seconde = ordonnance(
            rythme = Rythme.UnJourSurN(2),
            dateDebut = LocalDate.of(2026, 1, 4),
            dateAncrage = LocalDate.of(2026, 1, 1),
        )

        assertFalse(estJourActif(seconde, LocalDate.of(2026, 1, 4)))
        assertTrue(estJourActif(seconde, LocalDate.of(2026, 1, 5)))
        assertFalse(estJourActif(seconde, LocalDate.of(2026, 1, 6)))
        assertTrue(estJourActif(seconde, LocalDate.of(2026, 1, 7)))
    }
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `./gradlew :domain:test --tests "fr.pillulier.domain.PlanningTest"`
Expected: échec de compilation — `No value passed for parameter 'dateAncrage'` (ou `Unresolved reference`). C'est le bon échec : le champ n'existe pas.

- [ ] **Step 3: Ajouter `dateAncrage` au domaine**

Dans `Types.kt`, sur `data class Ordonnance`, après `dateFin` :

```kotlin
    /**
     * Origine du rythme, distincte de [dateDebut] qui borne cette version.
     * Les versions successives d'une meme prescription la partagent, sans quoi
     * scinder une ordonnance « un jour sur N » decalerait sa phase.
     */
    val dateAncrage: LocalDate,
```

Dans `Planning.kt`, remplacer la branche `UnJourSurN` :

```kotlin
        is Rythme.UnJourSurN ->
            (date.toEpochDay() - ordonnance.dateAncrage.toEpochDay()) % rythme.n == 0L
```

Puis propager le paramètre dans le helper `ordonnance(...)` de `PlanningTest.kt` et de `StockTest.kt` avec la valeur par défaut `dateAncrage: LocalDate = dateDebut`.

- [ ] **Step 4: Lancer les tests du domaine**

Run: `./gradlew :domain:test`
Expected: PASS, y compris les tests existants de `PlanningTest` et `StockTest`.

- [ ] **Step 5: Ajouter les colonnes aux entités**

Dans `Entites.kt`, sur `MedicamentEntity`, après `critique` :

```kotlin
    /** Non nul = retire de la liste et du planning, mais son historique demeure. */
    val archiveLe: Instant? = null,
```

Sur `OrdonnanceEntity`, remplacer le KDoc et l'index, et ajouter la colonne :

```kotlin
/** Un medicament porte N ordonnances, disjointes dans le temps. */
@Entity(
    tableName = "ordonnance",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicamentId", "dateDebut"])],
)
```

et dans le corps de `OrdonnanceEntity`, après `dateFin` :

```kotlin
    val dateAncrage: LocalDate,
```

Sur `EvenementPriseEntity`, remplacer `onDelete = ForeignKey.CASCADE` par :

```kotlin
            onDelete = ForeignKey.RESTRICT,
```

- [ ] **Step 6: Mettre à jour les mappeurs**

Dans `Mappeurs.kt`, dans `OrdonnanceAvecDosesEntity.versDomaine()`, ajouter après `dateFin` :

```kotlin
        dateAncrage = ordonnance.dateAncrage,
```

Dans `Ordonnance.versEntite()`, ajouter après `dateFin = dateFin` :

```kotlin
        dateAncrage = dateAncrage,
```

Ajouter de même `archiveLe = archiveLe` dans les deux sens pour `Medicament` / `MedicamentEntity`, et `archiveLe: Instant?` sur `data class Medicament` dans `Types.kt`, après `critique`, avec la valeur par défaut `null`.

- [ ] **Step 7: Écrire la migration**

Créer `app/src/main/kotlin/fr/pillulier/app/data/db/Migrations.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Premiere migration du projet. SQLite ne sait ni retirer un index unique ni
 * changer une cle etrangere en place : les deux tables concernees sont
 * recreees puis recopiees.
 *
 * `dateAncrage` reprend `dateDebut` : l'ordonnance existante devient la version
 * qui couvre tout le passe, et son rythme garde son origine.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicament ADD COLUMN archiveLe INTEGER DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE ordonnance_nouvelle (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                medicamentId INTEGER NOT NULL,
                type TEXT NOT NULL,
                rythmeType TEXT NOT NULL,
                rythmeJours TEXT,
                rythmeN INTEGER,
                dateDebut INTEGER NOT NULL,
                dateFin INTEGER,
                dateAncrage INTEGER NOT NULL,
                FOREIGN KEY (medicamentId) REFERENCES medicament(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO ordonnance_nouvelle
                (id, medicamentId, type, rythmeType, rythmeJours, rythmeN, dateDebut, dateFin, dateAncrage)
            SELECT id, medicamentId, type, rythmeType, rythmeJours, rythmeN, dateDebut, dateFin, dateDebut
            FROM ordonnance
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE ordonnance")
        db.execSQL("ALTER TABLE ordonnance_nouvelle RENAME TO ordonnance")
        db.execSQL("CREATE INDEX index_ordonnance_medicamentId_dateDebut ON ordonnance(medicamentId, dateDebut)")

        db.execSQL(
            """
            CREATE TABLE evenement_prise_nouveau (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                medicamentId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                moment TEXT,
                doseReelle REAL NOT NULL,
                enregistreLe INTEGER NOT NULL,
                FOREIGN KEY (medicamentId) REFERENCES medicament(id) ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO evenement_prise_nouveau
                (id, medicamentId, date, moment, doseReelle, enregistreLe)
            SELECT id, medicamentId, date, moment, doseReelle, enregistreLe
            FROM evenement_prise
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE evenement_prise")
        db.execSQL("ALTER TABLE evenement_prise_nouveau RENAME TO evenement_prise")
        db.execSQL(
            "CREATE UNIQUE INDEX index_evenement_prise_medicamentId_date_moment " +
                "ON evenement_prise(medicamentId, date, moment)",
        )
        db.execSQL("CREATE INDEX index_evenement_prise_date ON evenement_prise(date)")
    }
}
```

- [ ] **Step 8: Passer la base en version 2 et brancher la migration**

Dans `PillulierDatabase.kt`, `version = 1` devient `version = 2`.

Dans `app/src/main/kotlin/fr/pillulier/app/di/ModuleDonnees.kt:25`, ajouter `.addMigrations(MIGRATION_1_2)` sur la chaine `Room.databaseBuilder(...)`, avant `.build()`.

- [ ] **Step 9: Exposer les schémas exportés aux tests JVM**

Dans `app/build.gradle.kts`, juste après la ligne `sourceSets["test"].kotlin.srcDir("src/test/kotlin")` :

```kotlin
    // MigrationTestHelper lit les schemas exportes depuis les assets du module de test.
    sourceSets["test"].assets.srcDir("$projectDir/schemas")
```

- [ ] **Step 10: Compiler pour produire le schéma 2**

Run: `./gradlew :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL, et `app/schemas/fr.pillulier.app.data.db.PillulierDatabase/2.json` existe.

Vérifier : `ls app/schemas/fr.pillulier.app.data.db.PillulierDatabase/`

- [ ] **Step 11: Écrire le test de migration**

Créer `app/src/test/kotlin/fr/pillulier/app/data/db/MigrationTest.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private const val BASE = "migration-test"

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val aide = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PillulierDatabase::class.java,
    )

    @Test
    fun `la migration 1 vers 2 ancre le rythme sur la date de debut`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                    "rythmeN, dateDebut, dateFin) " +
                    "VALUES (1, 1, 'PLANIFIEE', 'UN_JOUR_SUR_N', NULL, 2, 20000, NULL)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        migree.query("SELECT dateAncrage, dateDebut FROM ordonnance WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(20000L, it.getLong(0))
            assertEquals(it.getLong(1), it.getLong(0))
        }
    }

    @Test
    fun `la migration 1 vers 2 conserve les prises enregistrees`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO evenement_prise (id, medicamentId, date, moment, doseReelle, enregistreLe) " +
                    "VALUES (1, 1, 20000, 'MATIN', 1.0, 1700000000000)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        migree.query("SELECT COUNT(*) FROM evenement_prise").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `apres migration un medicament porte plusieurs ordonnances`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                    "rythmeN, dateDebut, dateFin) " +
                    "VALUES (1, 1, 'PLANIFIEE', 'TOUS_LES_JOURS', NULL, NULL, 20000, 20009)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        // L'index unique a disparu : une seconde version doit passer.
        migree.execSQL(
            "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                "rythmeN, dateDebut, dateFin, dateAncrage) " +
                "VALUES (2, 1, 'PLANIFIEE', 'TOUS_LES_JOURS', NULL, NULL, 20010, NULL, 20000)",
        )

        migree.query("SELECT COUNT(*) FROM ordonnance WHERE medicamentId = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
    }
}
```

- [ ] **Step 12: Lancer les tests de migration**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.data.db.MigrationTest"`
Expected: PASS, les trois.

Si `MigrationTestHelper` ne trouve pas les schémas, vérifier que l'étape 9 a bien été appliquée et que `app/schemas/fr.pillulier.app.data.db.PillulierDatabase/1.json` et `2.json` existent tous les deux.

- [ ] **Step 13: Réparer les appelants et lancer la suite complète**

Les constructions de `Ordonnance(...)` dans le code applicatif et les tests doivent recevoir `dateAncrage`. À ce stade, **toutes** passent `dateAncrage = dateDebut` : le comportement ne change pas. Sites connus : `EnregistrerMedicament.kt:54`, et les helpers des tests de `usecase/`.

Run: `./gradlew test`
Expected: PASS, 0 échec, 0 erreur.

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -F - <<'MSG'
feat(data): ancre le rythme et ouvre la table ordonnance au versionnement

Prepare le passage a N ordonnances par medicament sans rien changer au
comportement : l index unique sur medicamentId tombe, dateAncrage apparait et
reprend dateDebut partout, archiveLe attend son usage.

estJourActif compte desormais les jours de UnJourSurN depuis dateAncrage.
Scinder une ordonnance « un jour sur deux » sans cela decalerait sa phase : une
prescription du 1er janvier scindee le 4 donnerait les 1, 3, 4, 6.

La cle etrangere de evenement_prise passe en RESTRICT : l historique des prises
ne peut plus etre efface en silence par une suppression de medicament.

Premiere migration du projet, celle que le schema en version 1 sans
fallbackToDestructiveMigration reservait.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 2: `enVigueur` et les deux lecteurs qui supposaient l'unicité

Sans cette tâche, créer une seconde version casserait `ObserverStock` et `AujourdhuiViewModel` **en silence**. Elle doit donc précéder tout changement d'écriture.

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/OrdonnanceDao.kt:21-23`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/DepotOrdonnances.kt:20-21`
- Modify: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverStock.kt:42-45`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/aujourdhui/AujourdhuiViewModel.kt:53-57`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt:70`
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Versions.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/VersionsTest.kt` (créé)
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverStockTest.kt`

**Interfaces:**
- Consumes: `Ordonnance.dateAncrage` (Task 1)
- Produces: `fun List<OrdonnanceAvecDoses>.enVigueur(medicamentId: Long, date: LocalDate): OrdonnanceAvecDoses?` dans `fr.pillulier.domain`
- Produces: `suspend fun DepotOrdonnances.enVigueur(medicamentId: Long, date: LocalDate): OrdonnanceAvecDoses?`

- [ ] **Step 1: Écrire le test de sélection de version**

Créer `domain/src/test/kotlin/fr/pillulier/domain/VersionsTest.kt` :

```kotlin
package fr.pillulier.domain

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionsTest {

    private fun version(
        id: Long,
        debut: LocalDate,
        fin: LocalDate?,
        dose: Double,
    ) = OrdonnanceAvecDoses(
        ordonnance = Ordonnance(
            id = id,
            medicamentId = 1,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = debut,
            dateFin = fin,
            dateAncrage = LocalDate.of(2026, 1, 1),
        ),
        doses = listOf(DosePrescrite(Moment.MATIN, dose)),
    )

    private val versions = listOf(
        version(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), dose = 1.0),
        version(2, LocalDate.of(2026, 1, 10), null, dose = 2.0),
    )

    @Test
    fun `rend la version qui couvre la date`() {
        assertEquals(1L, versions.enVigueur(1, LocalDate.of(2026, 1, 5))?.ordonnance?.id)
        assertEquals(2L, versions.enVigueur(1, LocalDate.of(2026, 1, 20))?.ordonnance?.id)
    }

    @Test
    fun `rend la derniere version anterieure quand aucune ne couvre la date`() {
        val closes = listOf(
            version(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), dose = 1.0),
            version(2, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15), dose = 2.0),
        )

        assertEquals(2L, closes.enVigueur(1, LocalDate.of(2026, 2, 1))?.ordonnance?.id)
    }

    @Test
    fun `rend la premiere version quand la date precede tout`() {
        assertEquals(1L, versions.enVigueur(1, LocalDate.of(2025, 12, 1))?.ordonnance?.id)
    }

    @Test
    fun `rend null pour un medicament sans ordonnance`() {
        assertNull(versions.enVigueur(medicamentId = 99, date = LocalDate.of(2026, 1, 5)))
    }
}
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `./gradlew :domain:test --tests "fr.pillulier.domain.VersionsTest"`
Expected: échec de compilation — `Unresolved reference 'enVigueur'`.

- [ ] **Step 3: Implémenter la sélection de version**

Créer `domain/src/main/kotlin/fr/pillulier/domain/Versions.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate

/**
 * La version d'ordonnance d'un medicament active a cette date.
 *
 * Quand aucune ne couvre la date, rend la plus recente qui precede : la fiche
 * d'un medicament archive doit montrer sa derniere prescription connue plutot
 * qu'un formulaire vide. A defaut, la premiere a venir, pour qu'une ordonnance
 * qui demarre demain soit deja lisible. Nul seulement si le medicament n'a
 * aucune ordonnance.
 */
fun List<OrdonnanceAvecDoses>.enVigueur(
    medicamentId: Long,
    date: LocalDate,
): OrdonnanceAvecDoses? {
    val siennes = filter { it.ordonnance.medicamentId == medicamentId }

    return siennes.firstOrNull { couvre(it.ordonnance, date) }
        ?: siennes.filter { it.ordonnance.dateDebut <= date }.maxByOrNull { it.ordonnance.dateDebut }
        ?: siennes.minByOrNull { it.ordonnance.dateDebut }
}

private fun couvre(ordonnance: Ordonnance, date: LocalDate): Boolean =
    date >= ordonnance.dateDebut && (ordonnance.dateFin?.let { date <= it } ?: true)
```

- [ ] **Step 4: Lancer le test pour le voir passer**

Run: `./gradlew :domain:test --tests "fr.pillulier.domain.VersionsTest"`
Expected: PASS, les quatre.

- [ ] **Step 5: Écrire le test du stock multi-versions**

Dans `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverStockTest.kt`, ajouter le helper puis le test. L'horloge figee du fichier est au 5 janvier 2026.

```kotlin
    private suspend fun medicamentAvecDeuxVersions(
        doseAncienne: Double,
        doseCourante: Double,
        stock: Double,
    ): Long {
        val id = DepotMedicaments(base.medicaments()).enregistrer(
            Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )

        fun version(debut: LocalDate, fin: LocalDate?, dose: Double) = OrdonnanceAvecDoses(
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = debut,
                dateFin = fin,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, dose)),
        )

        // Insertion directe : enregistrerVersion n'existe qu'a la tache 3.
        listOf(
            version(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4), doseAncienne),
            version(LocalDate.of(2026, 1, 5), null, doseCourante),
        ).forEach { v ->
            val ordonnanceId = base.ordonnances().insererOrdonnance(v.ordonnance.versEntite())
            base.ordonnances().insererDoses(v.doses.map { it.versEntite(ordonnanceId) })
        }

        return id
    }
```

```kotlin
    @Test
    fun `la projection de stock utilise la version en vigueur aujourd hui`() = runTest {
        // Version close hier a 1 comprime par jour, version courante a 4 par jour.
        // Avec 8 unites en stock, la bonne version epuise en 2 jours, pas en 8.
        val id = medicamentAvecDeuxVersions(doseAncienne = 1.0, doseCourante = 4.0, stock = 8.0)

        val ligne = observerStock().first().single { it.medicamentId == id }

        assertEquals(2L, ligne.joursRestants)
    }
```

- [ ] **Step 6: Lancer le test pour le voir échouer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.ObserverStockTest"`
Expected: échec — `joursRestants` vaut 8, parce que `associateBy` a retenu l'ancienne version.

- [ ] **Step 7: Corriger les deux lecteurs et le dépôt**

Dans `OrdonnanceDao.kt`, remplacer `pourMedicament` :

```kotlin
    @Transaction
    @Query("SELECT * FROM ordonnance WHERE medicamentId = :medicamentId ORDER BY dateDebut")
    suspend fun versionsDe(medicamentId: Long): List<OrdonnanceAvecDosesEntity>
```

Dans `DepotOrdonnances.kt`, remplacer `pourMedicament` :

```kotlin
    /** La version active a cette date, ou la plus proche — voir `enVigueur`. */
    suspend fun enVigueur(medicamentId: Long, date: LocalDate): OrdonnanceAvecDoses? =
        dao.versionsDe(medicamentId).map { it.versDomaine() }.enVigueur(medicamentId, date)

    suspend fun versionsDe(medicamentId: Long): List<OrdonnanceAvecDoses> =
        dao.versionsDe(medicamentId).map { it.versDomaine() }
```

Dans `ObserverStock.kt`, remplacer la ligne 42 et l'accès ligne 46 :

```kotlin
        val aujourdhui = horloge.aujourdhui()
```
(remonter cette ligne avant l'usage), puis remplacer `val parMedicament = ...` par rien et, dans le `map`, remplacer `val ordonnance = parMedicament[medicament.id]` par :

```kotlin
            val ordonnance = toutesOrdonnances.enVigueur(medicament.id, aujourdhui)
```

Dans `AujourdhuiViewModel.kt`, remplacer le calcul de `idsALaDemande` :

```kotlin
        val aujourdhui = horloge.aujourdhui()
        val idsALaDemande = tous
            .map { it.id }
            .filter { id ->
                toutesOrdonnances.enVigueur(id, aujourdhui)?.ordonnance?.type ==
                    TypeOrdonnance.A_LA_DEMANDE
            }
            .toSet()
```

Dans `EditionViewModel.kt:70`, remplacer :

```kotlin
        val ordonnance = ordonnances.enVigueur(medicamentId, horloge.aujourdhui())
```

- [ ] **Step 8: Lancer la suite complète**

Run: `./gradlew test`
Expected: PASS, 0 échec.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -F - <<'MSG'
feat(data): selectionne la version d ordonnance en vigueur a une date

Deux lecteurs supposaient qu un medicament ne porte qu une ordonnance et
auraient casse en silence des la premiere seconde version. ObserverStock les
indexait par associateBy, qui en retient une arbitrairement et pouvait projeter
le stock depuis une prescription perimee. AujourdhuiViewModel derivait la liste
des medicaments a la demande du type des ordonnances, et en aurait compte un
dans les deux listes apres un changement de type.

enVigueur rend la version qui couvre la date, a defaut la plus recente qui
precede — la fiche d un medicament archive montre ainsi sa derniere
prescription connue plutot qu un formulaire vide.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 3: La règle de la date d'effet

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/DepotOrdonnances.kt:27-44`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/OrdonnanceDao.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt:29-68`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerMedicamentTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/HistoriqueImmuableTest.kt` (créé)

**Interfaces:**
- Consumes: `DepotOrdonnances.versionsDe`, `enVigueur` (Task 2)
- Produces: `suspend fun DepotOrdonnances.enregistrerVersion(medicamentId: Long, ordonnance: Ordonnance, doses: List<DosePrescrite>, dateEffet: LocalDate)`
- Produces: `EnregistrerMedicament.invoke(..., doses, dateEffet: LocalDate)` — nouveau dernier paramètre, sans valeur par défaut

- [ ] **Step 1: Écrire le test de non-régression — celui qui justifie le chantier**

Créer `app/src/test/kotlin/fr/pillulier/app/usecase/HistoriqueImmuableTest.kt`. Reprendre le montage de base de `EnregistrerMedicamentTest` (base Room en memoire, `momentsParDefaut`, `ProgrammateurEspion`, `HorlogeFigee`), avec l'horloge au 15 janvier 2026, puis ce helper :

```kotlin
    private suspend fun medicamentQuotidienDepuisLePremierJanvier(): Long {
        enregistrerMedicament(
            medicament = Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        return medicaments.tous().single().id
    }
```

Puis les deux tests :

```kotlin
    @Test
    fun `changer de rythme ne touche pas au planning des jours passes`() = runTest {
        val id = medicamentQuotidienDepuisLePremierJanvier()
        val avant = (1..14).map { jour ->
            jour to prisesAttendues(
                LocalDate.of(2026, 1, jour),
                ordonnances.toutes(),
                moments.heures(),
            ).count { it.medicamentId == id }
        }

        // Le 15, le medecin passe a un jour sur deux.
        enregistrerMedicament(
            medicament = medicaments.parId(id)!!,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.UnJourSurN(2),
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 15),
        )

        val apres = (1..14).map { jour ->
            jour to prisesAttendues(
                LocalDate.of(2026, 1, jour),
                ordonnances.toutes(),
                moments.heures(),
            ).count { it.medicamentId == id }
        }

        assertEquals(avant, apres, "aucune prise passee ne doit avoir bouge")
        assertTrue(avant.all { it.second == 1 }, "chaque jour passe portait bien une prise")
    }

    @Test
    fun `le nouveau rythme s applique a partir de la date d effet`() = runTest {
        val id = medicamentQuotidienDepuisLePremierJanvier()

        enregistrerMedicament(
            medicament = medicaments.parId(id)!!,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.UnJourSurN(2),
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 15),
        )

        fun compte(jour: Int) = prisesAttendues(
            LocalDate.of(2026, 1, jour),
            ordonnances.toutes(),
            moments.heures(),
        ).count { it.medicamentId == id }

        // Ancrage au 1er janvier : les jours impairs restent actifs.
        assertEquals(1, compte(15))
        assertEquals(0, compte(16))
        assertEquals(1, compte(17))
    }
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.HistoriqueImmuableTest"`
Expected: échec de compilation — `No value passed for parameter 'dateEffet'`.

- [ ] **Step 3: Implémenter la règle dans le dépôt**

Dans `OrdonnanceDao.kt`, ajouter :

```kotlin
    @Query("DELETE FROM ordonnance WHERE medicamentId = :medicamentId AND dateDebut >= :dateEffet")
    suspend fun supprimerVersionsDepuis(medicamentId: Long, dateEffet: LocalDate)

    @Query(
        "UPDATE ordonnance SET dateFin = :veille " +
            "WHERE medicamentId = :medicamentId AND dateDebut < :dateEffet " +
            "AND (dateFin IS NULL OR dateFin >= :dateEffet)",
    )
    suspend fun cloturerVersionsAvant(medicamentId: Long, dateEffet: LocalDate, veille: LocalDate)
```

Dans `DepotOrdonnances.kt`, remplacer `enregistrer` par :

```kotlin
    /**
     * La nouvelle version prend tout a partir de [dateEffet] : les versions
     * anterieures sont cloturees la veille, celles qui commencent a cette date
     * ou apres disparaissent. Une seule regle, qui couvre la creation, la
     * modification du jour et la correction retroactive.
     *
     * Rien n'est ecrit si la version en vigueur prescrit deja exactement la
     * meme chose : renommer un medicament ne doit pas couper son historique.
     */
    @Transaction
    suspend fun enregistrerVersion(
        medicamentId: Long,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ) {
        val existantes = versionsDe(medicamentId)
        val courante = existantes.enVigueur(medicamentId, dateEffet)

        if (courante != null && prescritLaMemeChose(courante, ordonnance, doses)) return

        val ancrage = courante?.ordonnance?.dateAncrage?.coerceAtMost(dateEffet) ?: dateEffet

        dao.supprimerVersionsDepuis(medicamentId, dateEffet)
        dao.cloturerVersionsAvant(medicamentId, dateEffet, dateEffet.minusDays(1))

        val id = dao.insererOrdonnance(
            ordonnance.copy(
                id = 0,
                medicamentId = medicamentId,
                dateDebut = dateEffet,
                dateAncrage = ancrage,
            ).versEntite(),
        )
        dao.insererDoses(doses.map { it.versEntite(id) })
    }

    private fun prescritLaMemeChose(
        courante: OrdonnanceAvecDoses,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
    ): Boolean =
        courante.ordonnance.type == ordonnance.type &&
            courante.ordonnance.rythme == ordonnance.rythme &&
            courante.ordonnance.dateFin == ordonnance.dateFin &&
            courante.doses.sortedBy { it.moment } == doses.sortedBy { it.moment }
```

Ajouter `import androidx.room.Transaction` et les imports `LocalDate`, `enVigueur`.

- [ ] **Step 4: Brancher `EnregistrerMedicament`**

Dans `EnregistrerMedicament.kt`, ajouter le paramètre `dateEffet: LocalDate` après `doses`, et remplacer l'appel :

```kotlin
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = type,
                rythme = rythme,
                dateDebut = dateEffet,
                dateFin = dateFin,
                dateAncrage = dateEffet,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
            dateEffet = maxOf(dateEffet, dateDebut),
        )
```

Ajouter la garde, avec les autres `require` :

```kotlin
        require(!dateEffet.isBefore(dateDebut)) {
            "la date d effet ne peut pas preceder le debut du traitement"
        }
```

- [ ] **Step 5: Lancer les tests**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.HistoriqueImmuableTest"`
Expected: PASS, les deux.

- [ ] **Step 6: Ajouter les tests de la règle**

Dans `EnregistrerMedicamentTest.kt`, les appels existants reçoivent `dateEffet = dateDebut`. Ajouter le helper, puis les trois cas.

```kotlin
    private suspend fun medicamentQuotidien(): Long {
        enregistrerMedicament(
            medicament = Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        return medicaments.tous().single().id
    }
```

```kotlin
    @Test
    fun `renommer un medicament ne cree pas de version`() = runTest {
        val id = medicamentQuotidien()
        val avant = ordonnances.versionsDe(id).size

        enregistrerMedicament(
            medicament = medicaments.parId(id)!!.copy(nom = "Levothyrox 100"),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 10),
        )

        assertEquals(avant, ordonnances.versionsDe(id).size)
        assertEquals("Levothyrox 100", medicaments.parId(id)!!.nom)
    }

    @Test
    fun `une seconde modification a la meme date remplace la premiere`() = runTest {
        val id = medicamentQuotidien()

        repeat(2) { tour ->
            enregistrerMedicament(
                medicament = medicaments.parId(id)!!,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.UnJourSurN(tour + 2),
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
                dateEffet = LocalDate.of(2026, 1, 10),
            )
        }

        val versions = ordonnances.versionsDe(id)
        assertEquals(2, versions.size, "l originale cloturee, plus une seule nouvelle")
        assertEquals(Rythme.UnJourSurN(3), versions.last().ordonnance.rythme)
    }

    @Test
    fun `la version precedente est cloturee la veille de la date d effet`() = runTest {
        val id = medicamentQuotidien()

        enregistrerMedicament(
            medicament = medicaments.parId(id)!!,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.UnJourSurN(2),
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 10),
        )

        val versions = ordonnances.versionsDe(id).sortedBy { it.ordonnance.dateDebut }
        assertEquals(LocalDate.of(2026, 1, 9), versions.first().ordonnance.dateFin)
        assertEquals(LocalDate.of(2026, 1, 10), versions.last().ordonnance.dateDebut)
    }
```

- [ ] **Step 7: Lancer la suite complète**

Run: `./gradlew test`
Expected: PASS, 0 échec.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -F - <<'MSG'
feat(usecase): une modification d ordonnance ouvre une version datee

Modifier une prescription mettait l ordonnance a jour en place, si bien que le
planning des jours passes se recalculait depuis la prescription du jour. Passer
de « tous les jours » a « un jour sur deux » faisait disparaitre une prise
passee sur deux.

La nouvelle version prend tout a partir de sa date d effet : les versions
anterieures sont cloturees la veille, celles qui commencent a cette date ou
apres disparaissent. Une seule regle, qui couvre la creation, la modification du
jour, la correction retroactive et la re-correction.

Reecrire le passe reste possible sur demande explicite, en datant la
modification. Ce qui disparait, c est la reecriture accidentelle, jusqu ici
comportement par defaut et invisible.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 4: Archivage au lieu de suppression

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/MedicamentDao.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/DepotMedicaments.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt:77-99`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/MedicamentsViewModel.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverStock.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ArchiverMedicamentTest.kt` (créé)

**Interfaces:**
- Consumes: `Medicament.archiveLe` (Task 1), `DepotOrdonnances.enregistrerVersion` (Task 3)
- Produces: `ArchiverMedicament.invoke(medicamentId: Long)` — remplace `SupprimerMedicament`
- Produces: `DepotMedicaments.observerActifs(): Flow<List<Medicament>>`, `DepotMedicaments.archiver(id: Long, quand: Instant)`
- Garantie : `DepotMedicaments.tous()` continue de rendre **tous** les médicaments, archivés compris — `ReArmerRappels` en dépend pour annuler son surensemble d'alarmes

- [ ] **Step 1: Écrire les tests d'archivage**

Créer `app/src/test/kotlin/fr/pillulier/app/usecase/ArchiverMedicamentTest.kt`, sur le modèle de `CloturerJourneeTest` pour le montage de la base, avec `HorlogeFigee(LocalDateTime.of(2026, 1, 6, 9, 0))` et ce helper :

```kotlin
    private suspend fun medicamentQuotidienAvecUnePrise(jour: LocalDate): Long {
        enregistrerMedicament(
            medicament = Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        val id = medicaments.tous().single().id
        enregistrerPrise(id, jour, Moment.MATIN, dose = 1.0)
        return id
    }
```

Puis les trois tests :

```kotlin
    @Test
    fun `archiver conserve les prises passees`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 3)).size)
    }

    @Test
    fun `archiver ferme le planning a partir de demain`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        fun compte(jour: LocalDate) = prisesAttendues(
            jour,
            ordonnances.toutes(),
            moments.heures(),
        ).count { it.medicamentId == id }

        assertEquals(1, compte(horloge.aujourdhui()), "la journee en cours reste servie")
        assertEquals(0, compte(horloge.aujourdhui().plusDays(1)))
    }

    @Test
    fun `un medicament archive sort de la liste mais reste connu du moteur`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        assertTrue(medicaments.observerActifs().first().none { it.id == id })
        assertTrue(
            medicaments.tous().any { it.id == id },
            "ReArmerRappels batit son surensemble sur tous() : masquer l archive y laisserait une alarme vivante",
        )
    }
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.ArchiverMedicamentTest"`
Expected: échec de compilation — `Unresolved reference 'ArchiverMedicament'`, `'observerActifs'`.

- [ ] **Step 3: Ajouter les requêtes et le dépôt**

Dans `MedicamentDao.kt`, remplacer `supprimer` et ajouter :

```kotlin
    @Query("SELECT * FROM medicament WHERE archiveLe IS NULL ORDER BY nom COLLATE NOCASE")
    fun observerActifs(): Flow<List<MedicamentEntity>>

    @Query("UPDATE medicament SET archiveLe = :quand WHERE id = :id")
    suspend fun archiver(id: Long, quand: Instant)
```

Garder `observerTous()` et `tous()` inchangés. Supprimer la requête `supprimer` : plus aucun appelant, et la clé étrangère `RESTRICT` la ferait échouer.

Dans `DepotMedicaments.kt`, remplacer `supprimer` par :

```kotlin
    fun observerActifs(): Flow<List<Medicament>> =
        dao.observerActifs().map { entites -> entites.map { it.versDomaine() } }

    suspend fun archiver(id: Long, quand: Instant) = dao.archiver(id, quand)
```

- [ ] **Step 4: Remplacer `SupprimerMedicament` par `ArchiverMedicament`**

Dans `EnregistrerMedicament.kt`, remplacer la classe `SupprimerMedicament` :

```kotlin
/**
 * Un medicament ne se supprime pas : ses prises passees sont un journal du reel
 * et doivent survivre au traitement. On ferme sa fenetre d alarmes, on clot son
 * ordonnance ce soir, puis on le marque archive.
 *
 * La fenetre est fermee **avant** le marquage, comme elle l etait avant la
 * suppression : le rearmement batit son surensemble sur `medicaments.tous()`,
 * et une alarme laissee vivante deviendrait inatteignable — indeboulonnable si
 * elle est critique.
 */
class ArchiverMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val reArmerRappels: ReArmerRappels,
    private val horloge: Horloge,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(medicamentId: Long) {
        val aujourdhui = horloge.aujourdhui()

        (1L until ReArmerRappels.JOURS_FENETRE).forEach { decalage ->
            val jour = aujourdhui.plusDays(decalage)
            Moment.entries.forEach { moment ->
                programmateur.annuler(CleRappel(medicamentId, jour, moment))
                notifications.retirer(CleRappel(medicamentId, jour, moment))
            }
        }

        ordonnances.cloturerA(medicamentId, aujourdhui)
        medicaments.archiver(medicamentId, horloge.instant())

        reArmerRappels()
        rafraichirWidget()
    }
}
```

Dans `DepotOrdonnances.kt`, ajouter :

```kotlin
    /** Ferme toutes les versions encore ouvertes au-dela de [dateFin]. */
    suspend fun cloturerA(medicamentId: Long, dateFin: LocalDate) =
        dao.cloturerOuvertes(medicamentId, dateFin)
```

et dans `OrdonnanceDao.kt` :

```kotlin
    @Query(
        "UPDATE ordonnance SET dateFin = :dateFin " +
            "WHERE medicamentId = :medicamentId AND (dateFin IS NULL OR dateFin > :dateFin)",
    )
    suspend fun cloturerOuvertes(medicamentId: Long, dateFin: LocalDate)
```

- [ ] **Step 5: Basculer les lectures utilisateur sur `observerActifs`**

Dans `MedicamentsViewModel.kt`, `depot.observerTous()` devient `depot.observerActifs()`.

Dans `ObserverStock.kt`, `medicaments.observerTous()` devient `medicaments.observerActifs()`.

`ObserverAlertes` dérive de `ObserverStock` : rien à y faire.

Ne **pas** toucher `ObserverJournee` ni `ObserverSemaine` : ils lisent `observerTous()` pour pouvoir nommer un médicament archivé dans l'affichage du passé.

- [ ] **Step 6: Réparer les appelants de `SupprimerMedicament`**

`EditionViewModel.kt` injecte `SupprimerMedicament` et expose `supprimer()`. Renommer en `ArchiverMedicament` / `archiver()`. Mettre à jour `EditionViewModelTest.kt` en conséquence.

- [ ] **Step 7: Lancer la suite complète**

Run: `./gradlew test`
Expected: PASS, 0 échec.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -F - <<'MSG'
feat(usecase): archiver un medicament au lieu de l effacer

Supprimer un medicament effacait toutes ses prises passees, par la cascade de
evenement_prise. Un traitement termine emportait son historique.

ArchiverMedicament remplace SupprimerMedicament : il ferme la fenetre d alarmes,
clot l ordonnance ce soir puis marque le medicament archive. Le passe reste
lisible avec son nom.

tous() continue de rendre les archives, parce que ReArmerRappels y batit le
surensemble de cles qu il annule ; seules les trois lectures qui s adressent a
l utilisateur — liste, stock, alertes — les masquent.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

### Task 5: Date d'effet et dialogue d'archivage dans l'écran d'édition

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModelTest.kt`

**Interfaces:**
- Consumes: `EnregistrerMedicament.invoke(..., dateEffet)` (Task 3), `ArchiverMedicament` (Task 4)
- Produces: `EtatEdition.dateEffet: LocalDate`, `EditionViewModel.modifierDateEffet(LocalDate)`

- [ ] **Step 1: Écrire les tests du ViewModel**

Dans `EditionViewModelTest.kt` :

```kotlin
    @Test
    fun `la date d effet part sur aujourd hui`() = runTest {
        assertEquals(horloge.aujourdhui(), vue.etat.value.dateEffet)
    }

    @Test
    fun `la date d effet saisie est celle transmise a l enregistrement`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id
        vue.charger(id).join()

        vue.modifierDateEffet(LocalDate.of(2026, 1, 10))
        vue.definirDose(Moment.SOIR, 2.0)
        vue.enregistrer().join()

        val versions = ordonnances.versionsDe(id).sortedBy { it.ordonnance.dateDebut }
        assertEquals(LocalDate.of(2026, 1, 10), versions.last().ordonnance.dateDebut)
    }

    @Test
    fun `charger remet la date d effet sur aujourd hui`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        vue.charger(id).join()

        assertEquals(horloge.aujourdhui(), vue.etat.value.dateEffet)
    }
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.medicaments.EditionViewModelTest"`
Expected: échec de compilation — `Unresolved reference 'dateEffet'`.

- [ ] **Step 3: Ajouter la date d'effet au ViewModel**

Dans `EtatEdition`, après `dateFin` :

```kotlin
    /** Date a partir de laquelle la prescription saisie s'applique. Jamais persistee. */
    val dateEffet: LocalDate = LocalDate.now(),
```

Initialiser dans le constructeur (`EtatEdition(dateDebut = horloge.aujourdhui(), dateEffet = horloge.aujourdhui())`) et dans `charger()` — la date d'effet repart à aujourd'hui à chaque ouverture, elle ne se relit pas de la base.

Ajouter :

```kotlin
    fun modifierDateEffet(valeur: LocalDate) = _etat.update { it.copy(dateEffet = valeur) }
```

Et passer `dateEffet = etat.dateEffet` à l'appel de `enregistrerMedicament`.

- [ ] **Step 4: Lancer le test pour le voir passer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.medicaments.EditionViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Ajouter le sélecteur à l'écran**

Dans `EditionEcran.kt`, dans le bloc `if (etat.type == TypeOrdonnance.PLANIFIEE)`, après `SelecteurDateOptionnelle` de la date de fin :

```kotlin
            // Visible en modification seulement : a la creation, « Début du
            // traitement » joue deja ce role et un second champ egarerait.
            if (etat.id != 0L) {
                SelecteurDate(
                    libelle = "S'applique à partir du",
                    date = etat.dateEffet,
                    surChangement = vue::modifierDateEffet,
                )
            }
```

- [ ] **Step 6: Corriger le dialogue de suppression**

Toujours dans `EditionEcran.kt`, dans l'`AlertDialog` de confirmation : remplacer le titre et le texte, qui annoncent aujourd'hui l'effacement des prises.

```kotlin
            title = { Text("Archiver ce médicament ?") },
            text = {
                Text(
                    "Il disparaîtra de la liste et des prochains jours. " +
                        "Ses prises déjà enregistrées restent dans l'historique.",
                )
            },
```

et le bouton de confirmation porte « Archiver » au lieu de « Supprimer », en appelant `vue.archiver()`.

Mettre à jour aussi le commentaire au-dessus de la déclaration du dialogue, qui décrit la cascade désormais retirée.

- [ ] **Step 7: Lancer la suite complète et construire l'APK**

Run: `./gradlew test :app:assembleDebug`
Expected: PASS, 0 échec, BUILD SUCCESSFUL.

- [ ] **Step 8: Mettre à jour le README**

Trois retouches precises :

1. Section « Limites connues », puce « Une seule migration Room possible » : elle annonce un schema en version 1 « sans `fallbackToDestructiveMigration` — deliberement, pour que le premier changement de schema s'accompagne d'une vraie migration ». Ce premier changement vient d'avoir lieu. Reecrire la puce au passe, en pointant `MIGRATION_1_2` et son test.
2. Tableau des cinq ecrans, ligne « Mes médicaments » : ajouter qu'une modification d'ordonnance ouvre une version datee, et que le planning des jours passes reste fige sur la prescription d'alors.
3. Meme tableau et corps du texte : remplacer toute mention de suppression d'un medicament par l'archivage, en disant que les prises passees survivent.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -F - <<'MSG'
feat(ui): date d effet a la modification, archivage au lieu de suppression

L ecran d edition gagne « S'applique à partir du », pre-rempli a aujourd hui et
visible en modification seulement : a la creation, « Début du traitement » joue
deja ce role.

Le dialogue de suppression annoncait que les prises seraient effacees, ce qui
n est plus vrai. Il devient « Archiver » et dit que l historique est conserve.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Vérification finale

- [ ] `./gradlew test` — 0 échec, 0 erreur, 0 ignoré
- [ ] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [ ] Le scénario du bug, sur l'appareil : créer un médicament quotidien, cocher des prises sur plusieurs jours, reculer dans la vue semaine, passer l'ordonnance en « un jour sur deux », revenir dans la semaine passée — rien n'a bougé
- [ ] Une base créée en version 1 migre sans perte : le test de migration le couvre, mais une installation par-dessus l'ancienne version sur l'appareil le confirme
