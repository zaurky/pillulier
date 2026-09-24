package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerMedicament
    private lateinit var archiver: ArchiverMedicament
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
        enregistrer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
        archiver = ArchiverMedicament(
            medicaments = medicaments,
            ordonnances = ordonnances,
            programmateur = programmateur,
            notifications = Notifications(ApplicationProvider.getApplicationContext()),
            reArmerRappels = reArmer,
            horloge = horloge,
            rafraichirWidget = rafraichirWidget,
        )
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

    @Test
    fun `enregistrer cree le medicament et son ordonnance`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(1, ordonnances.versionsDe(id).single().doses.size)
    }

    @Test
    fun `enregistrer rearme les rappels`() = runTest {
        enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        assertEquals(3, programmateur.programmees.size)
    }

    @Test
    fun `une ordonnance planifiee sans dose est refusee`() = runTest {
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
    }

    @Test
    fun `un nom vide est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox().copy(nom = "  "),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
                dateEffet = LocalDate.of(2026, 1, 1),
            )
        }
    }

    @Test
    fun `une date de fin avant la date de debut est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
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
    fun `une ordonnance a la demande accepte l absence de dose`() = runTest {
        val id = enregistrer(
            medicament = levothyrox().copy(nom = "Doliprane", seuilAlerteJours = null, seuilAlerteUnites = 5),
            type = TypeOrdonnance.A_LA_DEMANDE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = emptyList(),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        assertTrue(ordonnances.versionsDe(id).single().doses.isEmpty())
    }

    @Test
    fun `reenregistrer un medicament existant le met a jour sans le dupliquer`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        enregistrer(
            medicament = levothyrox(id = id).copy(dosage = "100 µg"),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        assertEquals(1, medicaments.tous().size)
        assertEquals("100 µg", medicaments.parId(id)!!.dosage)
        assertEquals(listOf(Moment.SOIR), ordonnances.versionsDe(id).single().doses.map { it.moment })
    }

    @Test
    fun `archiver un medicament clot son ordonnance et le marque archive`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        archiver(id)

        assertNotNull(medicaments.parId(id)!!.archiveLe, "le medicament reste connu, marque archive")
        assertEquals(
            LocalDate.of(2026, 1, 5),
            ordonnances.versionsDe(id).single().ordonnance.dateFin,
            "l ordonnance est cloturee ce soir, pas effacee",
        )
    }

    @Test
    fun `archiver annule les alarmes du medicament avant de l archiver`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        programmateur.annulees.clear()

        archiver(id)

        // La fenetre a partir de demain, les quatre moments : le rearmement qui
        // suit reconstruit deja aujourd hui et hier sur son propre surensemble.
        val attendues = (1L until ReArmerRappels.JOURS_FENETRE).flatMap { decalage ->
            Moment.entries.map { moment ->
                CleRappel(id, LocalDate.of(2026, 1, 5).plusDays(decalage), moment)
            }
        }
        assertTrue(
            programmateur.annulees.containsAll(attendues),
            "les alarmes a venir du medicament archive doivent etre annulees",
        )
    }

    private suspend fun medicamentQuotidien(): Long {
        enregistrer(
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

    @Test
    fun `renommer un medicament ne cree pas de version`() = runTest {
        val id = medicamentQuotidien()
        val avant = ordonnances.versionsDe(id).size

        enregistrer(
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
            enregistrer(
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

        enregistrer(
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
}
