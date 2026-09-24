package fr.pillulier.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DepotOrdonnancesTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var moments: DepotMoments

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        moments = DepotMoments(base.moments())
    }

    @After
    fun fermer() = base.close()

    private fun medicament(nom: String = "Kardegic") = Medicament(
        id = 0,
        nom = nom,
        dosage = "75 mg",
        forme = Forme.SACHET,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = null,
        seuilAlerteUnites = null,
        critique = false,
    )

    @Test
    fun `enregistrer une ordonnance puis la relire conserve le rythme et les doses`() = runTest {
        val medicamentId = medicaments.enregistrer(medicament())

        ordonnances.enregistrerVersion(
            medicamentId = medicamentId,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.JoursDeSemaine(setOf(java.time.DayOfWeek.MONDAY)),
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        val relue = ordonnances.versionsDe(medicamentId).single()

        assertEquals(Rythme.JoursDeSemaine(setOf(java.time.DayOfWeek.MONDAY)), relue.ordonnance.rythme)
        assertEquals(1, relue.doses.size)
    }

    @Test
    fun `reenregistrer une ordonnance remplace ses doses sans les cumuler`() = runTest {
        val medicamentId = medicaments.enregistrer(medicament())
        val ordonnance = Ordonnance(
            id = 0,
            medicamentId = medicamentId,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            dateAncrage = LocalDate.of(2026, 1, 1),
        )

        val dateEffet = LocalDate.of(2026, 1, 1)
        ordonnances.enregistrerVersion(medicamentId, ordonnance, listOf(DosePrescrite(Moment.MATIN, 1.0)), dateEffet)
        ordonnances.enregistrerVersion(medicamentId, ordonnance, listOf(DosePrescrite(Moment.SOIR, 2.0)), dateEffet)

        val relue = ordonnances.versionsDe(medicamentId).single()

        assertEquals(listOf(Moment.SOIR), relue.doses.map { it.moment })
        assertEquals(2.0, relue.doses.single().dose)
    }

    @Test
    fun `une prescription identique a la version en vigueur supprime quand meme les versions plus tardives`() = runTest {
        val medicamentId = medicaments.enregistrer(medicament())
        val quotidien = Ordonnance(
            id = 0,
            medicamentId = medicamentId,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            dateAncrage = LocalDate.of(2026, 1, 1),
        )
        val doses = listOf(DosePrescrite(Moment.MATIN, 1.0))

        // Version en vigueur au 1er janvier, puis une seconde version qui prend
        // le relai le 10 : deux versions distinctes, dans l ordre.
        ordonnances.enregistrerVersion(medicamentId, quotidien, doses, LocalDate.of(2026, 1, 1))
        ordonnances.enregistrerVersion(
            medicamentId,
            quotidien.copy(rythme = Rythme.UnJourSurN(2)),
            doses,
            LocalDate.of(2026, 1, 10),
        )
        assertEquals(2, ordonnances.versionsDe(medicamentId).size)

        // La version du 1er janvier a ete cloturee la veille de celle du 10 :
        // reecrire exactement ce qu elle prescrit desormais (dateFin comprise)
        // est donc "la meme chose" pour la version en vigueur au 1er janvier.
        val memeChoseQuAujourdhuiEnVigueur = quotidien.copy(dateFin = LocalDate.of(2026, 1, 9))

        // Le court-circuit "rien a ecrire" ne doit pas empecher la suppression
        // de la version du 10, qui commence pourtant a ou apres cette date
        // d effet.
        ordonnances.enregistrerVersion(medicamentId, memeChoseQuAujourdhuiEnVigueur, doses, LocalDate.of(2026, 1, 1))

        val versions = ordonnances.versionsDe(medicamentId)
        assertEquals(1, versions.size, "la version du 10 janvier doit avoir disparu")
        assertEquals(Rythme.TousLesJours, versions.single().ordonnance.rythme)
    }

    @Test
    fun `les heures des moments sont modifiables`() = runTest {
        moments.definir(Moment.MATIN, LocalTime.of(7, 30))

        assertEquals(LocalTime.of(7, 30), moments.heures().getValue(Moment.MATIN))
        assertEquals(LocalTime.of(12, 0), moments.heures().getValue(Moment.MIDI))
    }
}
