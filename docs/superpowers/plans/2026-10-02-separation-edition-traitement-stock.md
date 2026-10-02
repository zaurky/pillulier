# Séparation de l'édition traitement / stock — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scinder la modification d'un médicament en deux écrans — Traitement (prescription + critique) depuis l'onglet Traitement, Stock (fiche) depuis l'onglet Stock — la création restant un formulaire unique.

**Architecture:** `EnregistrerMedicament` se décompose en deux cas d'usage feuilles, `EnregistrerFicheMedicament` (ligne `medicament`) et `EnregistrerPrescription` (version d'ordonnance), qu'il compose pour la création. `EditionViewModel` n'écrit plus que la prescription en modification (plus `critique` sur fiche relue). Un nouvel `EditionStockViewModel` + `EditionStockEcran` sous `ui/stock/` édite la fiche ; `CorrigerStock` disparaît.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (BOM 2025.03.00, Material3), Hilt, Room, tests JVM Robolectric (`@Config(sdk = [34])`), `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-10-01-separation-edition-traitement-stock-design.md`

## Global Constraints

- Toolchain prouvée sur cette machine : Gradle 9.3.1, AGP 8.12.0, Kotlin 2.1.20, SDK 36, JBR Java 25. Ne pas toucher aux versions.
- Lancer les tests avec `./gradlew :app:testDebugUnitTest --tests "<classe>"` depuis la racine du worktree.
- **Au premier échec de test inattendu, s'arrêter et demander à l'utilisateur s'il faut insister** — ne pas boucler sur des corrections successives. (L'échec attendu de l'étape « vérifier qu'il échoue » n'en est pas un.)
- Les deux onglets du bas (Traitement, Stock) ne changent pas.
- La création reste un formulaire complet unique, ouvert par le « + » de l'onglet Traitement.
- La liste de l'onglet Traitement (`MedicamentsEcran`) reste telle quelle.
- Commentaires et messages de commit en français sans accents dans le code Kotlin existant (`l ecran`, `medicament`) — suivre le style du fichier touché. Les libellés d'écran gardent leurs accents.
- Messages de commit : `type(portee): description` en minuscules, corps explicatif, terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Tests : classe Robolectric `@RunWith(AndroidJUnit4::class) @Config(sdk = [34])`, base Room en mémoire avec `momentsParDefaut`, doubles `HorlogeFigee` et `ProgrammateurEspion` déjà présents dans `fr.pillulier.app.usecase` (sources de test).

## Review Focus

1. **Une création refusée pour sa prescription ne doit laisser aucun médicament orphelin** — composer `fiche` puis `prescription` insérerait la ligne avant de refuser une ordonnance sans dose. Test : Task 1, `une creation refusee pour sa prescription n ecrit aucun medicament`.
2. **Le stock qui bouge pendant que l'écran Traitement est ouvert** (prise validée depuis une notification, « +1 boîte ») ne doit pas être ramené en arrière à l'enregistrement. Test : Task 2, `modifier le traitement n ecrase pas un stock change pendant l edition`.
3. **Un médicament archivé et/ou critique corrigé depuis l'écran Stock** doit rester archivé et critique — `@Update` réécrit la ligne entière. Test : Task 3, `enregistrer reporte critique et archivage`.
4. **L'écran Stock ouvert sans médicament chargé** (id illisible, médicament introuvable) ne doit jamais insérer une fiche sans prescription. Test : Task 3, `sans medicament charge enregistrer n ecrit rien`.
5. **Un seuil tapé de travers** (« 7j ») ne doit pas effacer en silence le réglage au profit du défaut. Test : Task 3, `un seuil illisible affiche une erreur et n ecrit rien`.

---

### Task 1: Scinder `EnregistrerMedicament` en deux cas d'usage feuilles

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt:1-85` (les trois classes ; `ArchiverMedicament` en dessous ne bouge pas)
- Create: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerFicheMedicamentTest.kt`
- Create: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerPrescriptionTest.kt`
- Modify: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerMedicamentTest.kt` (ajout de deux tests en fin de classe, rien d'existant ne change)

**Interfaces:**
- Consumes: `DepotMedicaments.enregistrer(Medicament): Long`, `DepotOrdonnances.enregistrerVersion(medicamentId, ordonnance, doses, dateEffet)`, `ReArmerRappels()`, `RafraichirWidget()`.
- Produces:
  - `class EnregistrerFicheMedicament @Inject constructor(medicaments: DepotMedicaments, reArmerRappels: ReArmerRappels, rafraichirWidget: RafraichirWidget)` avec `suspend operator fun invoke(medicament: Medicament): Long`
  - `class EnregistrerPrescription @Inject constructor(ordonnances: DepotOrdonnances, reArmerRappels: ReArmerRappels, rafraichirWidget: RafraichirWidget)` avec `suspend operator fun invoke(medicamentId: Long, type: TypeOrdonnance, rythme: Rythme, dateDebut: LocalDate, dateFin: LocalDate?, doses: List<DosePrescrite>, dateEffet: LocalDate)`
  - `EnregistrerMedicament` : constructeur **et** `invoke` inchangés (les quatre appels existants dans les tests restent valides).

- [ ] **Step 1: Écrire `EnregistrerFicheMedicamentTest`**

Créer `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerFicheMedicamentTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerFicheMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var fiche: EnregistrerFicheMedicament
    private lateinit var creer: EnregistrerMedicament
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(ApplicationProvider.getApplicationContext())
        fiche = EnregistrerFicheMedicament(medicaments, reArmer, rafraichirWidget)
        creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
    }

    @After
    fun fermer() = base.close()

    private fun levothyrox(id: Long = 0) = Medicament(
        id = id,
        nom = "Levothyrox",
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = false,
    )

    private suspend fun medicamentQuotidien(): Long = creer(
        medicament = levothyrox(),
        type = TypeOrdonnance.PLANIFIEE,
        rythme = Rythme.TousLesJours,
        dateDebut = LocalDate.of(2026, 1, 1),
        dateFin = null,
        doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        dateEffet = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `renommer n ecrit aucune version d ordonnance`() = runTest {
        val id = medicamentQuotidien()
        val avant = ordonnances.versionsDe(id)

        fiche(medicaments.parId(id)!!.copy(nom = "Levothyrox 100", stockUnites = 12.0))

        assertEquals("Levothyrox 100", medicaments.parId(id)!!.nom)
        assertEquals(12.0, medicaments.parId(id)!!.stockUnites)
        assertEquals(avant, ordonnances.versionsDe(id), "la fiche ne doit pas toucher aux ordonnances")
    }

    @Test
    fun `passer la fiche en critique rearme en alarme`() = runTest {
        val id = medicamentQuotidien()
        programmateur.programmees.clear()

        fiche(medicaments.parId(id)!!.copy(critique = true))

        assertTrue(programmateur.programmees.isNotEmpty(), "critique change le canal de rappel : il faut rearmer")
        assertTrue(programmateur.programmees.all { it.critique }, "les rappels rearmes doivent etre des alarmes")
    }

    @Test
    fun `un nom vide est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(nom = "  ")) }
        assertTrue(medicaments.tous().isEmpty())
    }

    @Test
    fun `une boite sans unite est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(unitesParBoite = 0)) }
        assertTrue(medicaments.tous().isEmpty())
    }

    @Test
    fun `un stock negatif est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(stockUnites = -1.0)) }
        assertTrue(medicaments.tous().isEmpty())
    }
}
```


- [ ] **Step 2: Écrire `EnregistrerPrescriptionTest`**

Créer `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerPrescriptionTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerPrescriptionTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var prescrire: EnregistrerPrescription
    private lateinit var creer: EnregistrerMedicament
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(ApplicationProvider.getApplicationContext())
        prescrire = EnregistrerPrescription(ordonnances, reArmer, rafraichirWidget)
        creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentQuotidien(): Long = creer(
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

    @Test
    fun `modifier la prescription ne touche pas au stock`() = runTest {
        val id = medicamentQuotidien()
        base.medicaments().ajusterStock(id, -4.0)

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
            dateEffet = LocalDate.of(2026, 1, 5),
        )

        assertEquals(26.0, medicaments.parId(id)!!.stockUnites)
        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(2, ordonnances.versionsDe(id).size)
    }

    @Test
    fun `une correction peut remonter avant le debut de la version courante`() = runTest {
        // Le garde dateEffet >= dateDebut ne vaut qu a la creation : il reste
        // dans EnregistrerMedicament et ne doit pas descendre ici.
        val id = medicamentQuotidien()

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
            dateEffet = LocalDate.of(2025, 12, 20),
        )

        assertEquals(
            LocalDate.of(2025, 12, 20),
            ordonnances.versionsDe(id).single().ordonnance.dateDebut,
        )
    }

    @Test
    fun `une ordonnance planifiee sans dose est refusee`() = runTest {
        val id = medicamentQuotidien()

        assertFailsWith<IllegalArgumentException> {
            prescrire(
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = emptyList(),
                dateEffet = LocalDate.of(2026, 1, 5),
            )
        }
        assertEquals(1, ordonnances.versionsDe(id).size)
    }

    @Test
    fun `une date de fin avant la date de debut est refusee`() = runTest {
        val id = medicamentQuotidien()

        assertFailsWith<IllegalArgumentException> {
            prescrire(
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 10),
                dateFin = LocalDate.of(2026, 1, 5),
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
                dateEffet = LocalDate.of(2026, 1, 10),
            )
        }
    }

    @Test
    fun `une ordonnance a la demande ignore les doses`() = runTest {
        val id = medicamentQuotidien()

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.A_LA_DEMANDE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 5),
        )

        val derniere = ordonnances.versionsDe(id).maxBy { it.ordonnance.dateDebut }
        assertEquals(TypeOrdonnance.A_LA_DEMANDE, derniere.ordonnance.type)
        assertTrue(derniere.doses.isEmpty())
    }
}
```

- [ ] **Step 3: Ajouter deux tests à `EnregistrerMedicamentTest`**

À la fin de la classe `EnregistrerMedicamentTest` (avant la dernière `}`), ajouter :

```kotlin
    @Test
    fun `une creation refusee pour sa prescription n ecrit aucun medicament`() = runTest {
        // Composer fiche puis prescription inserait la ligne avant de refuser :
        // toutes les regles doivent passer avant la premiere ecriture.
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = emptyList(),
                dateEffet = LocalDate.of(2026, 1, 1),
            )
        }

        assertTrue(medicaments.tous().isEmpty(), "aucun medicament sans prescription")
    }

    @Test
    fun `une creation avec un stock negatif est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox().copy(stockUnites = -1.0),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
                dateEffet = LocalDate.of(2026, 1, 1),
            )
        }
    }
```

- [ ] **Step 4: Vérifier que les nouveaux tests échouent**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.EnregistrerFicheMedicamentTest" --tests "fr.pillulier.app.usecase.EnregistrerPrescriptionTest" --tests "fr.pillulier.app.usecase.EnregistrerMedicamentTest"`
Expected: FAIL à la compilation — `Unresolved reference: EnregistrerFicheMedicament` et `EnregistrerPrescription`.

- [ ] **Step 5: Implémenter les trois classes**

Dans `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt`, remplacer tout ce qui précède le KDoc de `ArchiverMedicament` (lignes 1 à 85) par :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.LocalDate
import javax.inject.Inject

/**
 * Ecrit la fiche du medicament — identite, conditionnement, stock, seuils —
 * sans toucher a ses ordonnances, puis rearme : le nom figure dans les
 * notifications, et `critique` choisit entre notification et alarme.
 */
class EnregistrerFicheMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(medicament: Medicament): Long {
        require(medicament.nom.isNotBlank()) { "le nom du medicament est obligatoire" }
        require(medicament.unitesParBoite > 0) { "une boite contient au moins une unite" }
        // Reprise de CorrigerStock, seul chemin qui ecrivait le stock librement
        // avant que l ecran Stock n edite la fiche entiere.
        require(medicament.stockUnites >= 0.0) {
            "un stock ne peut pas etre negatif, recu ${medicament.stockUnites}"
        }

        val id = medicaments.enregistrer(medicament)

        reArmerRappels()
        rafraichirWidget()
        return id
    }
}

/**
 * Ecrit une nouvelle version de prescription a partir de [dateEffet], puis
 * rearme : toute modification de posologie doit se refleter tout de suite
 * dans les alarmes.
 */
class EnregistrerPrescription @Inject constructor(
    private val ordonnances: DepotOrdonnances,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ) {
        validerPrescription(type, dateDebut, dateFin, doses)

        ordonnances.enregistrerVersion(
            medicamentId = medicamentId,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = medicamentId,
                type = type,
                rythme = rythme,
                dateDebut = dateEffet,
                dateFin = dateFin,
                dateAncrage = dateEffet,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
            dateEffet = dateEffet,
        )

        reArmerRappels()
        rafraichirWidget()
    }
}

/**
 * Cree un medicament d un bloc : sa fiche et sa premiere prescription. Toutes
 * les regles passent avant la premiere ecriture — composer naivement les deux
 * feuilles insererait la fiche avant de refuser une ordonnance sans dose, et
 * laisserait un medicament sans prescription.
 */
class EnregistrerMedicament @Inject constructor(
    medicaments: DepotMedicaments,
    ordonnances: DepotOrdonnances,
    reArmerRappels: ReArmerRappels,
    rafraichirWidget: RafraichirWidget,
) {
    private val fiche = EnregistrerFicheMedicament(medicaments, reArmerRappels, rafraichirWidget)
    private val prescription = EnregistrerPrescription(ordonnances, reArmerRappels, rafraichirWidget)

    suspend operator fun invoke(
        medicament: Medicament,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ): Long {
        // Le garde ne tient qu a la creation, seul moment ou `dateDebut` porte
        // vraiment le debut du traitement. A la modification, l ecran l a rempli
        // avec le debut de la version en vigueur : le comparer refuserait de
        // renommer un traitement date du futur, et interdirait une correction
        // retroactive remontant avant la version courante — ce que
        // `supprimerVersionsDepuis` existe precisement pour servir. Le debut du
        // traitement, lui, se corrige lui aussi retroactivement : il n offre
        // donc aucune borne inferieure a opposer a la date d effet.
        if (medicament.id == 0L) {
            require(!dateEffet.isBefore(dateDebut)) {
                "la date d effet ne peut pas preceder le debut du traitement"
            }
        }
        validerPrescription(type, dateDebut, dateFin, doses)

        val id = fiche(medicament)
        prescription(id, type, rythme, dateDebut, dateFin, doses, dateEffet)
        return id
    }
}

private fun validerPrescription(
    type: TypeOrdonnance,
    dateDebut: LocalDate,
    dateFin: LocalDate?,
    doses: List<DosePrescrite>,
) {
    require(dateFin == null || !dateFin.isBefore(dateDebut)) {
        "la date de fin ne peut pas preceder la date de debut"
    }
    if (type == TypeOrdonnance.PLANIFIEE) {
        require(doses.isNotEmpty()) { "une ordonnance planifiee doit porter au moins une dose" }
        require(doses.all { it.dose > 0.0 }) { "une dose doit etre strictement positive" }
        require(doses.map { it.moment }.distinct().size == doses.size) {
            "un moment ne peut porter qu une dose"
        }
    }
}
```

Note : `EnregistrerMedicament` instancie lui-même ses feuilles plutôt que de les recevoir par injection, pour garder son constructeur à quatre paramètres — les quatre tests qui le construisent à la main (`EnregistrerMedicamentTest`, `EditionViewModelTest`, `HistoriqueImmuableTest`, `ArchiverMedicamentTest`) restent intacts. Le nom vide est désormais refusé par la feuille `fiche`, avant toute écriture : `validerPrescription` passe avant, et `fiche` valide avant d'écrire.

- [ ] **Step 6: Vérifier que tout passe**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.*"`
Expected: PASS — y compris tous les tests préexistants de `EnregistrerMedicamentTest`, `HistoriqueImmuableTest`, `ArchiverMedicamentTest`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt \
        app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerFicheMedicamentTest.kt \
        app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerPrescriptionTest.kt \
        app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerMedicamentTest.kt
git commit -F - <<'EOF'
refactor(usecase): scinde l enregistrement en fiche et prescription

EnregistrerMedicament ecrivait la ligne medicament et une version
d ordonnance d un bloc. Deux feuilles prennent chacune sa moitie :
EnregistrerFicheMedicament et EnregistrerPrescription. EnregistrerMedicament
les compose pour la creation, sans changer de signature.

Les regles de prescription passent toutes avant la premiere ecriture,
sinon une ordonnance sans dose serait refusee apres l insertion de la
fiche. La garde stock non negatif rejoint la fiche.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 2: L'écran Traitement n'écrit plus que le traitement en modification

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt:60-67` (constructeur), `:159-204` (`enregistrer`), `:230-264` (`ecrire`)
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt:127-160` (champs fiche), `:262-273` (seuils)
- Test: `app/src/test/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModelTest.kt`

**Interfaces:**
- Consumes: `EnregistrerPrescription`, `EnregistrerFicheMedicament` (Task 1).
- Produces: `EditionViewModel @Inject constructor(medicaments: DepotMedicaments, ordonnances: DepotOrdonnances, enregistrerMedicament: EnregistrerMedicament, enregistrerPrescription: EnregistrerPrescription, enregistrerFicheMedicament: EnregistrerFicheMedicament, archiverMedicament: ArchiverMedicament, horloge: Horloge)`.

- [ ] **Step 1: Adapter la préparation du test**

Dans `EditionViewModelTest.kt` :

Ajouter les imports :

```kotlin
import fr.pillulier.app.usecase.EnregistrerFicheMedicament
import fr.pillulier.app.usecase.EnregistrerPrescription
```

Ajouter les champs après `private lateinit var creer: EnregistrerMedicament` :

```kotlin
    private lateinit var prescrire: EnregistrerPrescription
    private lateinit var fiche: EnregistrerFicheMedicament
```

Dans `preparer()`, juste après `creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)` :

```kotlin
        prescrire = EnregistrerPrescription(ordonnances, reArmer, rafraichirWidget)
        fiche = EnregistrerFicheMedicament(medicaments, reArmer, rafraichirWidget)
```

Remplacer `creerVue()` par :

```kotlin
    private fun creerVue() {
        vue = EditionViewModel(
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerMedicament = creer,
            enregistrerPrescription = prescrire,
            enregistrerFicheMedicament = fiche,
            archiverMedicament = archive,
            horloge = horloge,
        )
    }
```

- [ ] **Step 2: Réécrire les deux tests qui renommaient en modification**

Le nom ne s'édite plus depuis l'écran Traitement en modification. Les deux tests gardent leur intention — ne pas exiger de toucher aux dates, ne pas désarchiver — mais passent par `critique`, seul champ de la fiche que cet écran édite encore.

Remplacer le test `renommer un traitement date du futur n exige pas de toucher aux dates` par :

```kotlin
    @Test
    fun `modifier un traitement date du futur n exige pas de toucher aux dates`() = runTest {
        // Creation le 5 janvier d un traitement qui ne demarre que le 1er fevrier.
        remplirFormulaireValide()
        vue.modifierDateDebut(LocalDate.of(2026, 2, 1))
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        // On rouvre l ecran le jour meme pour le passer en critique :
        // `charger` relit la date de debut de la version a venir, tandis que la
        // date d effet repart d aujourd hui.
        creerVue()
        vue.charger(id).join()
        vue.modifierCritique(true)
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur, "basculer critique ne doit rien exiger d autre")
        assertEquals(true, medicaments.parId(id)!!.critique)
        assertEquals(
            LocalDate.of(2026, 2, 1),
            ordonnances.versionsDe(id).single().ordonnance.dateDebut,
            "le debut du traitement ne doit pas avoir bouge",
        )
    }
```

Remplacer le test `enregistrer un medicament archive ne le desarchive pas` par :

```kotlin
    @Test
    fun `enregistrer un medicament archive ne le desarchive pas`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id
        archive(id)

        creerVue()
        vue.charger(id).join()
        vue.modifierCritique(true)
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertEquals(true, medicaments.parId(id)!!.critique)
        assertNotNull(
            medicaments.parId(id)!!.archiveLe,
            "l enregistrement remplace toute la ligne : il doit reporter la date d archivage",
        )
    }
```

- [ ] **Step 3: Ajouter les tests du nouveau comportement**

À la fin de la classe, ajouter :

```kotlin
    @Test
    fun `modifier le traitement n ecrase pas un stock change pendant l edition`() = runTest {
        remplirFormulaireValide()
        vue.modifierStock("30")
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        creerVue()
        vue.charger(id).join()
        // Une prise validee depuis une notification, ecran ouvert.
        base.medicaments().ajusterStock(id, -1.0)
        vue.definirDose(Moment.SOIR, 2.0)
        vue.modifierCritique(true)
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertEquals(29.0, medicaments.parId(id)!!.stockUnites, "le stock relu a l ouverture ne doit pas revenir")
        assertEquals(true, medicaments.parId(id)!!.critique)
    }

    @Test
    fun `en modification les champs de la fiche ne sont pas ecrits`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        creerVue()
        vue.charger(id).join()
        vue.modifierNom("Autre nom")
        vue.modifierStock("illisible")
        vue.definirDose(Moment.SOIR, 2.0)
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur, "un champ de fiche absent de l ecran ne peut pas bloquer")
        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(true, vue.etat.value.enregistre)
    }

    @Test
    fun `une prescription refusee en modification n ecrit pas critique`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        creerVue()
        vue.charger(id).join()
        vue.modifierCritique(true)
        vue.definirDose(Moment.MATIN, 0.0) // plus aucune dose : refusee
        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur)
        assertEquals(false, medicaments.parId(id)!!.critique, "un refus ne doit rien avoir ecrit")
    }
```

- [ ] **Step 4: Vérifier qu'ils échouent**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.medicaments.EditionViewModelTest"`
Expected: FAIL à la compilation — `No parameter with name 'enregistrerPrescription' found`.

- [ ] **Step 5: Implémenter dans `EditionViewModel`**

Ajouter les imports :

```kotlin
import fr.pillulier.app.usecase.EnregistrerFicheMedicament
import fr.pillulier.app.usecase.EnregistrerPrescription
```

Remplacer le constructeur :

```kotlin
@HiltViewModel
class EditionViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val enregistrerMedicament: EnregistrerMedicament,
    private val enregistrerPrescription: EnregistrerPrescription,
    private val enregistrerFicheMedicament: EnregistrerFicheMedicament,
    private val archiverMedicament: ArchiverMedicament,
    private val horloge: Horloge,
) : ViewModel() {
```

Dans `enregistrer()`, remplacer le début (de `val etat = _etat.value` jusqu'au garde du stock inclus, lignes 160-175) par :

```kotlin
        val etat = _etat.value

        // Un champ numérique non vide qui ne se lit pas est une erreur de saisie,
        // pas un zéro : le domaine ne reçoit qu'un nombre et ne pourrait plus
        // faire la différence. Seule la création les montre : en modification
        // la fiche s'édite depuis l'onglet Stock, et ces champs ne sont pas écrits.
        if (etat.id == 0L) {
            if (etat.unitesParBoite.isNotBlank() && etat.unitesParBoite.toIntOrNull() == null) {
                _etat.update { it.copy(erreur = "Unités par boîte : nombre illisible") }
                return@launch
            }
            if (etat.stockUnites.isNotBlank() && etat.stockUnites.replace(',', '.').toDoubleOrNull() == null) {
                _etat.update { it.copy(erreur = "Stock : nombre illisible") }
                return@launch
            }
        }
```

(Le garde de l'intervalle et tout ce qui suit restent tels quels.)

Remplacer `ecrire` (lignes 230-264) par :

```kotlin
    private suspend fun ecrire(etat: EtatEdition, dateEffet: LocalDate) {
        try {
            if (etat.id == 0L) creer(etat, dateEffet) else modifier(etat, dateEffet)
            _etat.update { it.copy(erreur = null, enregistre = true) }
        } catch (erreur: IllegalArgumentException) {
            _etat.update { it.copy(erreur = erreur.message) }
        }
    }

    private suspend fun creer(etat: EtatEdition, dateEffet: LocalDate) {
        enregistrerMedicament(
            medicament = Medicament(
                id = 0,
                nom = etat.nom.trim(),
                dosage = etat.dosage.trim(),
                forme = etat.forme,
                unitesParBoite = etat.unitesParBoite.toIntOrNull() ?: 0,
                stockUnites = etat.stockUnites.replace(',', '.').toDoubleOrNull() ?: 0.0,
                seuilAlerteJours = etat.seuilAlerteJours.toIntOrNull(),
                seuilAlerteUnites = etat.seuilAlerteUnites.toIntOrNull(),
                critique = etat.critique,
                archiveLe = etat.archiveLe,
            ),
            type = etat.type,
            rythme = etat.rythme,
            dateDebut = etat.dateDebut,
            dateFin = etat.dateFin,
            doses = dosesSaisies(etat),
            // Une creation n'a pas de passe a proteger : elle prend effet a la
            // date de debut saisie, meme passee, sous peine de perdre l'ancrage
            // du rythme (le champ « S'applique a partir du » n'est d'ailleurs
            // pas affiche a la creation, voir EditionEcran).
            dateEffet = dateEffet,
        )
    }

    /**
     * En modification l'ecran n'edite que le traitement. La prescription passe
     * d'abord : elle seule peut refuser la saisie, et un refus ne doit rien
     * avoir ecrit. `critique` vit sur la fiche ; on la relit fraiche plutot
     * que de reporter l'etat charge a l'ouverture, parce que le stock bouge
     * pendant que l'ecran est ouvert et qu'un `@Update` le ramenerait en arriere.
     */
    private suspend fun modifier(etat: EtatEdition, dateEffet: LocalDate) {
        enregistrerPrescription(
            medicamentId = etat.id,
            type = etat.type,
            rythme = etat.rythme,
            dateDebut = etat.dateDebut,
            dateFin = etat.dateFin,
            doses = dosesSaisies(etat),
            dateEffet = dateEffet,
        )

        val fiche = medicaments.parId(etat.id) ?: return
        if (fiche.critique != etat.critique) {
            enregistrerFicheMedicament(fiche.copy(critique = etat.critique))
        }
    }
```

- [ ] **Step 6: Masquer les champs de fiche dans `EditionEcran` en modification**

Dans `EditionEcran.kt`, envelopper les cinq champs de fiche (de `OutlinedTextField(value = etat.nom, ...)` ligne 127 jusqu'au champ « Stock actuel en unités » ligne 160 inclus) dans :

```kotlin
        // La fiche se saisit a la creation, puis se corrige depuis l'onglet
        // Stock : en modification cet ecran n'edite que le traitement.
        if (etat.id == 0L) {
            OutlinedTextField(
                value = etat.nom,
                onValueChange = vue::modifierNom,
                label = { Text("Nom") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = etat.dosage,
                onValueChange = vue::modifierDosage,
                label = { Text("Dosage") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Forme", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Forme.entries.forEach { forme ->
                    FilterChip(
                        selected = etat.forme == forme,
                        onClick = { vue.modifierForme(forme) },
                        label = { Text(libelleForme(forme, 1.0)) },
                    )
                }
            }

            OutlinedTextField(
                value = etat.unitesParBoite,
                onValueChange = vue::modifierUnitesParBoite,
                label = { Text("Unités par boîte") },
            )
            OutlinedTextField(
                value = etat.stockUnites,
                onValueChange = vue::modifierStock,
                label = { Text("Stock actuel en unités") },
            )
        }
```

Puis remplacer les lignes 262-273 — du `OutlinedTextField(value = etat.seuilAlerteJours, ...)` qui termine le bloc `if (etat.type == TypeOrdonnance.PLANIFIEE)`, jusqu'à l'accolade qui ferme son `else` — par le code ci-dessous. Sa première `}` referme le bloc `PLANIFIEE` :

```kotlin
            if (etat.id == 0L) {
                OutlinedTextField(
                    value = etat.seuilAlerteJours,
                    onValueChange = vue::modifierSeuilJours,
                    label = { Text("Alerter X jours avant la fin (vide = défaut)") },
                )
            }
        } else if (etat.id == 0L) {
            OutlinedTextField(
                value = etat.seuilAlerteUnites,
                onValueChange = vue::modifierSeuilUnites,
                label = { Text("Alerter sous X unités restantes") },
            )
        }
```

- [ ] **Step 7: Vérifier que tout passe et que l'app compile**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.medicaments.EditionViewModelTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS, puis BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt \
        app/src/test/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModelTest.kt
git commit -F - <<'EOF'
feat(ui): l edition du traitement n ecrit plus la fiche en modification

En modification, l ecran ne montre plus que la prescription et le
caractere critique. Il ecrit la prescription par EnregistrerPrescription,
puis critique sur la fiche relue en base : reporter l etat charge a
l ouverture ramenait en arriere un stock decremente entre-temps.

La creation garde son formulaire complet et passe toujours par
EnregistrerMedicament.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 3: `EditionStockViewModel`

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModel.kt`
- Create: `app/src/test/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModelTest.kt`

**Interfaces:**
- Consumes: `EnregistrerFicheMedicament` (Task 1), `DepotMedicaments.parId`, `DepotOrdonnances.enVigueur(medicamentId, date): OrdonnanceAvecDoses?`, `Horloge.aujourdhui()`.
- Produces:
  - `data class EtatEditionStock(id: Long, nom: String, dosage: String, forme: Forme, unitesParBoite: String, stockUnites: String, seuilAlerteJours: String, seuilAlerteUnites: String, aLaDemande: Boolean, critique: Boolean, archiveLe: Instant?, erreur: String?, enregistre: Boolean)`
  - `EditionStockViewModel` : `etat: StateFlow<EtatEditionStock>`, `charger(medicamentId: Long): Job`, `modifierNom/modifierDosage/modifierUnitesParBoite/modifierStock/modifierSeuilJours/modifierSeuilUnites(String)`, `modifierForme(Forme)`, `enregistrer(): Job`.

- [ ] **Step 1: Écrire le test**

Créer `app/src/test/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModelTest.kt` :

```kotlin
package fr.pillulier.app.ui.stock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.EnregistrerFicheMedicament
import fr.pillulier.app.usecase.EnregistrerMedicament
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EditionStockViewModelTest {

    private lateinit var contexte: Context
    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var creer: EnregistrerMedicament
    private lateinit var fiche: EnregistrerFicheMedicament
    private lateinit var vue: EditionStockViewModel
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        // `viewModelScope` lance sur le dispatcher Main, en pause sous Robolectric.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(contexte)
        creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
        fiche = EnregistrerFicheMedicament(medicaments, reArmer, rafraichirWidget)

        vue = EditionStockViewModel(
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerFicheMedicament = fiche,
            horloge = horloge,
        )
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

    private suspend fun medicament(
        type: TypeOrdonnance = TypeOrdonnance.PLANIFIEE,
        critique: Boolean = false,
    ): Long = creer(
        medicament = Medicament(
            id = 0,
            nom = "Doliprane",
            dosage = "500 mg",
            forme = Forme.COMPRIME,
            unitesParBoite = 16,
            stockUnites = 10.0,
            seuilAlerteJours = 5,
            seuilAlerteUnites = 4,
            critique = critique,
        ),
        type = type,
        rythme = Rythme.TousLesJours,
        dateDebut = LocalDate.of(2026, 1, 1),
        dateFin = null,
        doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        dateEffet = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `charger remplit la fiche depuis la base`() = runTest {
        val id = medicament()

        vue.charger(id).join()

        val etat = vue.etat.value
        assertEquals(id, etat.id)
        assertEquals("Doliprane", etat.nom)
        assertEquals("500 mg", etat.dosage)
        assertEquals(Forme.COMPRIME, etat.forme)
        assertEquals("16", etat.unitesParBoite)
        assertEquals("10.0", etat.stockUnites)
        assertEquals("5", etat.seuilAlerteJours)
        assertEquals("4", etat.seuilAlerteUnites)
        assertEquals(false, etat.aLaDemande)
    }

    @Test
    fun `charger lit le type de l ordonnance en vigueur`() = runTest {
        val id = medicament(type = TypeOrdonnance.A_LA_DEMANDE)

        vue.charger(id).join()

        assertEquals(true, vue.etat.value.aLaDemande)
    }

    @Test
    fun `un medicament sans ordonnance se charge comme planifie`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Orphelin",
                dosage = "",
                forme = Forme.COMPRIME,
                unitesParBoite = 10,
                stockUnites = 3.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )

        vue.charger(id).join()

        assertEquals("Orphelin", vue.etat.value.nom)
        assertEquals(false, vue.etat.value.aLaDemande)
    }

    @Test
    fun `renommer et recompter n ecrit aucune version d ordonnance`() = runTest {
        val id = medicament()
        val avant = ordonnances.versionsDe(id)
        vue.charger(id).join()

        vue.modifierNom("Doliprane 1000")
        vue.modifierStock("12,5")
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertEquals(true, vue.etat.value.enregistre)
        assertEquals("Doliprane 1000", medicaments.parId(id)!!.nom)
        assertEquals(12.5, medicaments.parId(id)!!.stockUnites)
        assertEquals(avant, ordonnances.versionsDe(id))
    }

    @Test
    fun `enregistrer reporte critique et archivage`() = runTest {
        val id = medicament(critique = true)
        base.medicaments().archiver(id, Instant.parse("2026-01-04T20:00:00Z"))
        vue.charger(id).join()

        vue.modifierStock("8")
        vue.enregistrer().join()

        val relu = medicaments.parId(id)!!
        assertEquals(8.0, relu.stockUnites)
        assertEquals(true, relu.critique, "l alarme critique ne doit pas s eteindre")
        assertNotNull(relu.archiveLe, "le medicament ne doit pas etre desarchive")
    }

    @Test
    fun `sans medicament charge enregistrer n ecrit rien`() = runTest {
        vue.charger(404).join()
        vue.modifierNom("Fantome")
        vue.modifierUnitesParBoite("10")

        vue.enregistrer().join()

        assertTrue(medicaments.tous().isEmpty(), "l ecran Stock ne cree jamais")
        assertEquals(false, vue.etat.value.enregistre)
    }

    @Test
    fun `un stock illisible affiche une erreur et n ecrit rien`() = runTest {
        val id = medicament()
        vue.charger(id).join()

        vue.modifierStock("3O")
        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur)
        assertEquals(10.0, medicaments.parId(id)!!.stockUnites)
        assertEquals(false, vue.etat.value.enregistre)
    }

    @Test
    fun `un stock negatif est refuse`() = runTest {
        val id = medicament()
        vue.charger(id).join()

        vue.modifierStock("-2")
        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur)
        assertEquals(10.0, medicaments.parId(id)!!.stockUnites)
    }

    @Test
    fun `un seuil illisible affiche une erreur et n ecrit rien`() = runTest {
        val id = medicament()
        vue.charger(id).join()

        vue.modifierSeuilJours("7j")
        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur, "un reglage ne doit pas s effacer en silence")
        assertEquals(5, medicaments.parId(id)!!.seuilAlerteJours)
    }

    @Test
    fun `un seuil vide revient au defaut`() = runTest {
        val id = medicament()
        vue.charger(id).join()

        vue.modifierSeuilJours("")
        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertNull(medicaments.parId(id)!!.seuilAlerteJours)
    }

    @Test
    fun `un second chargement ne rejette pas la saisie en cours`() = runTest {
        val id = medicament()
        vue.charger(id).join()

        vue.modifierNom("Doliprane 1000")
        vue.charger(id).join()

        assertEquals("Doliprane 1000", vue.etat.value.nom)
    }
}
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.stock.EditionStockViewModelTest"`
Expected: FAIL à la compilation — `Unresolved reference: EditionStockViewModel`.

- [ ] **Step 3: Implémenter**

Créer `app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerFicheMedicament
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EtatEditionStock(
    val id: Long = 0,
    val nom: String = "",
    val dosage: String = "",
    val forme: Forme = Forme.COMPRIME,
    val unitesParBoite: String = "",
    val stockUnites: String = "",
    val seuilAlerteJours: String = "",
    val seuilAlerteUnites: String = "",
    /** Lu sur l'ordonnance en vigueur, jamais édité ici : décide quel seuil s'affiche. */
    val aLaDemande: Boolean = false,
    /**
     * Ni l'un ni l'autre ne s'éditent sur cet écran, mais `@Update` réécrit la
     * ligne entière : les omettre désarchiverait le médicament et éteindrait
     * son alarme critique à chaque correction de stock.
     */
    val critique: Boolean = false,
    val archiveLe: Instant? = null,
    val erreur: String? = null,
    val enregistre: Boolean = false,
)

@HiltViewModel
class EditionStockViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val enregistrerFicheMedicament: EnregistrerFicheMedicament,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatEditionStock())
    val etat: StateFlow<EtatEditionStock> = _etat.asStateFlow()

    /** Meme garde que EditionViewModel : la rotation relance le `LaunchedEffect`. */
    private var dejaCharge = false

    fun charger(medicamentId: Long) = viewModelScope.launch {
        if (dejaCharge) return@launch
        dejaCharge = true

        val medicament = medicaments.parId(medicamentId) ?: return@launch
        val ordonnance = ordonnances.enVigueur(medicamentId, horloge.aujourdhui())

        _etat.value = EtatEditionStock(
            id = medicament.id,
            nom = medicament.nom,
            dosage = medicament.dosage,
            forme = medicament.forme,
            unitesParBoite = medicament.unitesParBoite.toString(),
            stockUnites = medicament.stockUnites.toString(),
            seuilAlerteJours = medicament.seuilAlerteJours?.toString() ?: "",
            seuilAlerteUnites = medicament.seuilAlerteUnites?.toString() ?: "",
            aLaDemande = ordonnance?.ordonnance?.type == TypeOrdonnance.A_LA_DEMANDE,
            critique = medicament.critique,
            archiveLe = medicament.archiveLe,
        )
    }

    fun modifierNom(valeur: String) = _etat.update { it.copy(nom = valeur) }
    fun modifierDosage(valeur: String) = _etat.update { it.copy(dosage = valeur) }
    fun modifierForme(valeur: Forme) = _etat.update { it.copy(forme = valeur) }
    fun modifierUnitesParBoite(valeur: String) = _etat.update { it.copy(unitesParBoite = valeur) }
    fun modifierStock(valeur: String) = _etat.update { it.copy(stockUnites = valeur) }
    fun modifierSeuilJours(valeur: String) = _etat.update { it.copy(seuilAlerteJours = valeur) }
    fun modifierSeuilUnites(valeur: String) = _etat.update { it.copy(seuilAlerteUnites = valeur) }

    fun enregistrer() = viewModelScope.launch {
        val etat = _etat.value

        // Cet ecran ne cree jamais : une fiche ecrite sans medicament charge
        // naitrait sans prescription, ce que la creation atomique interdit.
        if (etat.id == 0L) return@launch

        val unitesParBoite = etat.unitesParBoite.toIntOrNull()
        val stockUnites = etat.stockUnites.replace(',', '.').toDoubleOrNull()
        val seuilJours = etat.seuilAlerteJours.toIntOrNull()
        val seuilUnites = etat.seuilAlerteUnites.toIntOrNull()

        // Un champ non vide qui ne se lit pas est une erreur de saisie, pas un
        // zero ni un « defaut » : pour un seuil, la confusion effacerait en
        // silence un reglage sur une faute de frappe.
        val illisible = when {
            etat.unitesParBoite.isNotBlank() && unitesParBoite == null -> "Unités par boîte : nombre illisible"
            etat.stockUnites.isNotBlank() && stockUnites == null -> "Stock : nombre illisible"
            etat.seuilAlerteJours.isNotBlank() && seuilJours == null -> "Seuil d'alerte : nombre illisible"
            etat.seuilAlerteUnites.isNotBlank() && seuilUnites == null -> "Seuil d'alerte : nombre illisible"
            else -> null
        }
        if (illisible != null) {
            _etat.update { it.copy(erreur = illisible) }
            return@launch
        }

        try {
            enregistrerFicheMedicament(
                Medicament(
                    id = etat.id,
                    nom = etat.nom.trim(),
                    dosage = etat.dosage.trim(),
                    forme = etat.forme,
                    unitesParBoite = unitesParBoite ?: 0,
                    stockUnites = stockUnites ?: 0.0,
                    seuilAlerteJours = seuilJours,
                    seuilAlerteUnites = seuilUnites,
                    critique = etat.critique,
                    archiveLe = etat.archiveLe,
                ),
            )
            _etat.update { it.copy(erreur = null, enregistre = true) }
        } catch (erreur: IllegalArgumentException) {
            _etat.update { it.copy(erreur = erreur.message) }
        }
    }
}
```

- [ ] **Step 4: Vérifier que tout passe**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.ui.stock.EditionStockViewModelTest"`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModel.kt \
        app/src/test/kotlin/fr/pillulier/app/ui/stock/EditionStockViewModelTest.kt
git commit -F - <<'EOF'
feat(ui): ajoute la vue d edition de la fiche stock

EditionStockViewModel charge et ecrit la fiche seule par
EnregistrerFicheMedicament. Il reporte critique et archiveLe sans les
montrer, puisque l @Update reecrit la ligne entiere, et refuse
d enregistrer tant qu aucun medicament n est charge.

Un seuil illisible y devient une erreur au lieu de retomber en silence
sur la valeur par defaut.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 4: Écran d'édition stock, carte cliquable, route

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/ChampsFiche.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockEcran.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt` (le bloc `if (etat.id == 0L)` posé en Task 2)
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/stock/StockEcran.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/Navigation.kt`

**Interfaces:**
- Consumes: `EditionStockViewModel`, `EtatEditionStock` (Task 3).
- Produces: `@Composable fun ChampsFiche(nom: String, surNom: (String) -> Unit, dosage: String, surDosage: (String) -> Unit, forme: Forme, surForme: (Forme) -> Unit, unitesParBoite: String, surUnitesParBoite: (String) -> Unit, stockUnites: String, surStock: (String) -> Unit)`, `@Composable fun EditionStockEcran(medicamentId: Long, surSortie: () -> Unit, vue: EditionStockViewModel = hiltViewModel())`, `@Composable fun StockEcran(surSelection: (Long) -> Unit, vue: StockViewModel = hiltViewModel())`.

Pas de test automatisé : le dépôt n'a pas de tests d'UI Compose, et toute la logique vit dans les ViewModels testés en Task 2 et 3. La vérification est la compilation, puis le contrôle manuel de la Task 6.

- [ ] **Step 1: Extraire les champs de fiche partagés**

Les cinq champs existent désormais à deux endroits — création et écran Stock. Créer `app/src/main/kotlin/fr/pillulier/app/ui/ChampsFiche.kt` :

```kotlin
package fr.pillulier.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.pillulier.domain.Forme

/**
 * Identite et conditionnement d'un medicament. Emet ses champs dans la colonne
 * de l'appelant : la creation et l'ecran Stock les espacent a leur facon.
 */
@Composable
fun ChampsFiche(
    nom: String,
    surNom: (String) -> Unit,
    dosage: String,
    surDosage: (String) -> Unit,
    forme: Forme,
    surForme: (Forme) -> Unit,
    unitesParBoite: String,
    surUnitesParBoite: (String) -> Unit,
    stockUnites: String,
    surStock: (String) -> Unit,
) {
    OutlinedTextField(
        value = nom,
        onValueChange = surNom,
        label = { Text("Nom") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = dosage,
        onValueChange = surDosage,
        label = { Text("Dosage") },
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Forme", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Forme.entries.forEach { candidate ->
            FilterChip(
                selected = forme == candidate,
                onClick = { surForme(candidate) },
                label = { Text(libelleForme(candidate, 1.0)) },
            )
        }
    }

    OutlinedTextField(
        value = unitesParBoite,
        onValueChange = surUnitesParBoite,
        label = { Text("Unités par boîte") },
    )
    OutlinedTextField(
        value = stockUnites,
        onValueChange = surStock,
        label = { Text("Stock actuel en unités") },
    )
}
```

- [ ] **Step 2: L'utiliser dans `EditionEcran`**

Remplacer le contenu du bloc `if (etat.id == 0L) { ... }` posé en Task 2 Step 6 (les cinq champs) par :

```kotlin
        // La fiche se saisit a la creation, puis se corrige depuis l'onglet
        // Stock : en modification cet ecran n'edite que le traitement.
        if (etat.id == 0L) {
            ChampsFiche(
                nom = etat.nom,
                surNom = vue::modifierNom,
                dosage = etat.dosage,
                surDosage = vue::modifierDosage,
                forme = etat.forme,
                surForme = vue::modifierForme,
                unitesParBoite = etat.unitesParBoite,
                surUnitesParBoite = vue::modifierUnitesParBoite,
                stockUnites = etat.stockUnites,
                surStock = vue::modifierStock,
            )
        }
```

Ajouter `import fr.pillulier.app.ui.ChampsFiche`. Retirer `import fr.pillulier.app.ui.libelleForme` et `import fr.pillulier.domain.Forme` s'ils ne servent plus ailleurs dans le fichier (vérifier avec `grep -n "libelleForme\|Forme\." app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt`).

- [ ] **Step 3: Créer `EditionStockEcran`**

Créer `app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockEcran.kt` :

```kotlin
package fr.pillulier.app.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.ChampsFiche

@Composable
fun EditionStockEcran(
    medicamentId: Long,
    surSortie: () -> Unit,
    vue: EditionStockViewModel = hiltViewModel(),
) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LaunchedEffect(medicamentId) { vue.charger(medicamentId) }
    LaunchedEffect(etat.enregistre) { if (etat.enregistre) surSortie() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChampsFiche(
            nom = etat.nom,
            surNom = vue::modifierNom,
            dosage = etat.dosage,
            surDosage = vue::modifierDosage,
            forme = etat.forme,
            surForme = vue::modifierForme,
            unitesParBoite = etat.unitesParBoite,
            surUnitesParBoite = vue::modifierUnitesParBoite,
            stockUnites = etat.stockUnites,
            surStock = vue::modifierStock,
        )

        // Le seuil suit le type de prescription, comme a la creation : en jours
        // quand la consommation se prevoit, en unites quand elle est a la demande.
        if (etat.aLaDemande) {
            OutlinedTextField(
                value = etat.seuilAlerteUnites,
                onValueChange = vue::modifierSeuilUnites,
                label = { Text("Alerter sous X unités restantes") },
            )
        } else {
            OutlinedTextField(
                value = etat.seuilAlerteJours,
                onValueChange = vue::modifierSeuilJours,
                label = { Text("Alerter X jours avant la fin (vide = défaut)") },
            )
        }

        etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { vue.enregistrer() }) { Text("Enregistrer") }
    }
}
```

- [ ] **Step 4: Rendre la carte cliquable et retirer « Recompté » dans `StockEcran`**

Dans `StockEcran.kt` :

Remplacer la signature de `StockEcran` et les deux appels à `LigneStockCarte` :

```kotlin
@Composable
fun StockEcran(
    surSelection: (Long) -> Unit,
    vue: StockViewModel = hiltViewModel(),
) {
    val lignes by vue.lignes.collectAsStateWithLifecycle()
    val (planifies, aLaDemande) = lignes.partition { !it.aLaDemande }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        items(planifies) { ligne ->
            LigneStockCarte(ligne, surSelection, vue)
        }

        if (aLaDemande.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    "À la demande — consommation imprévisible",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(aLaDemande) { ligne ->
                LigneStockCarte(ligne, surSelection, vue)
            }
        }
    }
}
```

Remplacer `LigneStockCarte` entière par :

```kotlin
@Composable
private fun LigneStockCarte(ligne: LigneStock, surSelection: (Long) -> Unit, vue: StockViewModel) {
    // Le recomptage se saisit dans la fiche : un seul endroit pour une meme
    // valeur. « +1 boite » reste ici, geste d'une touche sans saisie.
    Card(
        onClick = { surSelection(ligne.medicamentId) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${ligne.nom} ${ligne.dosage}", style = MaterialTheme.typography.bodyLarge)
            Text(ligne.libelleUnites, style = MaterialTheme.typography.bodyMedium)

            if (ligne.joursRestants != null) {
                Text(
                    "Plus que ${ligne.joursRestants} jours · épuisement le " +
                        "${ligne.dateEpuisement?.format(formatDate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ligne.dateAlerte?.let {
                    Text("Alerte le ${it.format(formatDate)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (ligne.enAlerte) {
                Text(
                    "À renouveler",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = { vue.ajouterBoite(ligne.medicamentId) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("+1 boîte")
            }
        }
    }
}
```

Retirer les imports devenus inutiles : `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.Row`, `androidx.compose.material3.OutlinedTextField`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.Alignment`. Garder `androidx.compose.runtime.getValue` (utilisé par `val lignes by`).

`Card(onClick = ...)` est stable dans Material3 1.3 (BOM 2025.03.00). Si la compilation réclame un opt-in, ajouter `@OptIn(ExperimentalMaterial3Api::class)` sur `LigneStockCarte` avec l'import correspondant.

- [ ] **Step 5: Brancher la route dans `Navigation.kt`**

Ajouter l'import :

```kotlin
import fr.pillulier.app.ui.stock.EditionStockEcran
```

Remplacer `composable("stock") { StockEcran() }` par :

```kotlin
            composable("stock") {
                StockEcran(surSelection = { id -> controleur.navigate("stock/$id") })
            }
            composable("stock/{id}") { entreeRoute ->
                EditionStockEcran(
                    medicamentId = entreeRoute.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    surSortie = { controleur.popBackStack() },
                )
            }
```

- [ ] **Step 6: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Un avertissement « unused » sur `StockViewModel.corriger` est attendu : il disparaît en Task 5.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/ui/ChampsFiche.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/stock/EditionStockEcran.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/stock/StockEcran.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/Navigation.kt
git commit -F - <<'EOF'
feat(ui): ouvre la fiche stock depuis l onglet Stock

Taper une carte du stock ouvre EditionStockEcran : nom, dosage, forme,
unites par boite, stock et seuil d alerte. Le champ Recompte quitte la
carte, le recomptage se saisit desormais dans la fiche ; +1 boite reste.

Les cinq champs de fiche, maintenant partages entre la creation et cet
ecran, passent dans ChampsFiche.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 5: Retirer `CorrigerStock` et `definirStock`

**Files:**
- Delete: `app/src/main/kotlin/fr/pillulier/app/usecase/CorrigerStock.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/ui/stock/StockViewModel.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/MedicamentDao.kt:37-38`
- Modify: `app/src/test/kotlin/fr/pillulier/app/usecase/StockTest.kt`
- Modify: `app/src/test/kotlin/fr/pillulier/app/data/db/PillulierDatabaseTest.kt:91-98`

**Interfaces:**
- Consumes: la garde « stock non négatif » est déjà portée et testée par `EnregistrerFicheMedicament` (Task 1) — c'est ce qui autorise la suppression.
- Produces: `StockViewModel @Inject constructor(observerStock: ObserverStock, ajouterBoiteCas: AjouterBoite)`.

- [ ] **Step 1: Vérifier que la garde a bien été reprise avant de supprimer**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.EnregistrerFicheMedicamentTest"`
Expected: PASS, dont `un stock negatif est refuse`. Sinon, s'arrêter : supprimer `CorrigerStock` perdrait l'invariant.

- [ ] **Step 2: Supprimer le cas d'usage et sa méthode de DAO**

```bash
git rm app/src/main/kotlin/fr/pillulier/app/usecase/CorrigerStock.kt
```

Dans `MedicamentDao.kt`, supprimer :

```kotlin
    @Query("UPDATE medicament SET stockUnites = :valeur WHERE id = :id")
    suspend fun definirStock(id: Long, valeur: Double)
```

Remplacer `StockViewModel.kt` par :

```kotlin
package fr.pillulier.app.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.usecase.AjouterBoite
import fr.pillulier.app.usecase.LigneStock
import fr.pillulier.app.usecase.ObserverStock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    observerStock: ObserverStock,
    private val ajouterBoiteCas: AjouterBoite,
) : ViewModel() {

    val lignes: StateFlow<List<LigneStock>> = observerStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ajouterBoite(medicamentId: Long) = viewModelScope.launch {
        ajouterBoiteCas(medicamentId)
    }
}
```

- [ ] **Step 3: Retirer les tests du code supprimé**

Dans `StockTest.kt` : supprimer le champ `private lateinit var corrigerStock: CorrigerStock`, la ligne `corrigerStock = CorrigerStock(base.medicaments())`, et les deux tests `corriger le stock ecrase la valeur` et `corriger avec une valeur negative est refuse`. Les tests `ajouter une boite ...` restent.

Dans `PillulierDatabaseTest.kt`, supprimer le test entier :

```kotlin
    @Test
    fun `definir le stock ecrase la valeur`() = runTest {
        val id = base.medicaments().inserer(medicament(stock = 10.0))

        base.medicaments().definirStock(id, 42.0)

        assertEquals(42.0, base.medicaments().parId(id)!!.stockUnites)
    }
```

- [ ] **Step 4: Vérifier qu'il ne reste aucun appelant**

Run: `grep -rn "CorrigerStock\|definirStock\|corrigerStock" app/src`
Expected: aucune sortie.

- [ ] **Step 5: Vérifier les tests concernés**

Run: `./gradlew :app:testDebugUnitTest --tests "fr.pillulier.app.usecase.StockTest" --tests "fr.pillulier.app.data.db.PillulierDatabaseTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/kotlin/fr/pillulier/app/usecase/CorrigerStock.kt \
        app/src/main/kotlin/fr/pillulier/app/ui/stock/StockViewModel.kt \
        app/src/main/kotlin/fr/pillulier/app/data/db/MedicamentDao.kt \
        app/src/test/kotlin/fr/pillulier/app/usecase/StockTest.kt \
        app/src/test/kotlin/fr/pillulier/app/data/db/PillulierDatabaseTest.kt
git commit -F - <<'EOF'
refactor(stock): retire CorrigerStock et definirStock

La carte du stock etait le seul appelant de CorrigerStock. L ecran de
fiche ecrit le stock avec le reste de la ligne, par
EnregistrerFicheMedicament, qui porte desormais la garde stock non
negatif.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 6: Vérification complète et contrôle sur l'appareil

**Files:** aucun.

- [ ] **Step 1: Suite complète**

Run: `./gradlew :app:testDebugUnitTest :domain:test`
Expected: PASS sur l'ensemble. En cas d'échec, s'arrêter et demander (voir Global Constraints).

- [ ] **Step 2: Installer sur le Galaxy A35**

Run: `adb devices` (l'appareil doit apparaître en `device`), puis `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, `Installed on 1 device`.

- [ ] **Step 3: Contrôle manuel**

Cocher chaque point sur l'appareil :

- Onglet Traitement, « + » : le formulaire de création montre nom, dosage, forme, unités par boîte, stock, critique, prescription, seuil. Créer un médicament fonctionne.
- Onglet Traitement, taper un médicament : seuls critique, type, rythme, fin, « S'applique à partir du », doses et Archiver apparaissent. Aucun champ nom/dosage/forme/boîte/stock/seuil.
- Onglet Stock, taper une carte : l'écran fiche s'ouvre avec les six champs pré-remplis ; le seuil est en jours pour un planifié, en unités pour un à la demande.
- Onglet Stock : « +1 boîte » ajoute une boîte **sans** ouvrir la fiche. Le champ « Recompté » a disparu.
- Fiche stock : renommer, enregistrer → retour au stock, nouveau nom visible ; dans l'onglet Semaine, l'historique du médicament est intact.
- Fiche stock : taper « 7j » dans le seuil → message d'erreur, rien n'est écrit.
- Fiche stock : tourner le téléphone en cours de saisie → la saisie reste.
- Médicament critique : corriger son stock depuis la fiche, puis vérifier dans l'onglet Traitement qu'il est toujours critique.

- [ ] **Step 4: Signaler**

Rendre compte des points cochés et de tout écart, sans corriger à l'aveugle.
