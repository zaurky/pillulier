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
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.prisesAttendues
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression : modifier une ordonnance ne doit jamais recalculer le planning
 * des jours deja passes. Voir la note en tete de EnregistrerMedicament.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoriqueImmuableTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var moments: DepotMoments
    private lateinit var enregistrerMedicament: EnregistrerMedicament
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 15, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        moments = DepotMoments(base.moments())
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = moments,
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(ApplicationProvider.getApplicationContext())
        enregistrerMedicament = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
    }

    @After
    fun fermer() = base.close()

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

        suspend fun compte(jour: Int) = prisesAttendues(
            LocalDate.of(2026, 1, jour),
            ordonnances.toutes(),
            moments.heures(),
        ).count { it.medicamentId == id }

        // Ancrage au 1er janvier : les jours impairs restent actifs.
        assertEquals(1, compte(15))
        assertEquals(0, compte(16))
        assertEquals(1, compte(17))
    }
}
