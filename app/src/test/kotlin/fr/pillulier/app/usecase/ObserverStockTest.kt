package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.data.versEntite
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverStockTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverStock
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 9, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        observer = ObserverStock(
            medicaments = medicaments,
            ordonnances = ordonnances,
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, stock: Double): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        return id
    }

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
        //
        // La COURANTE est inseree en premier, a dessein. `associateBy` retient la
        // derniere occurrence d'une cle et `SELECT * FROM ordonnance` rend les
        // lignes par rowid : dans l'autre ordre, le code fautif retiendrait la
        // bonne version par accident et le test passerait avant le correctif.
        listOf(
            version(LocalDate.of(2026, 1, 5), null, doseCourante),
            version(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4), doseAncienne),
        ).forEach { v ->
            val ordonnanceId = base.ordonnances().insererOrdonnance(v.ordonnance.versEntite())
            base.ordonnances().insererDoses(v.doses.map { it.versEntite(ordonnanceId) })
        }

        return id
    }

    @Test
    fun `la ligne porte la date d epuisement et les jours restants`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        val ligne = observer().first().single()

        assertEquals(LocalDate.of(2026, 1, 15), ligne.dateEpuisement)
        assertEquals(10L, ligne.joursRestants)
        assertFalse(ligne.aLaDemande)
    }

    @Test
    fun `la date d alerte applique la marge du dimanche`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        // Epuisement le 15 janvier, seuil 7 jours : candidat le 8, un dimanche
        // tombe dans la fenetre le 11, donc l'alerte recule au 7.
        assertEquals(LocalDate.of(2026, 1, 7), observer().first().single().dateAlerte)
    }

    @Test
    fun `une ligne devient en alerte quand la date est atteinte`() = runTest {
        planifie("Levothyrox", stock = 3.0)

        assertTrue(observer().first().single().enAlerte)
    }

    @Test
    fun `un stock confortable n est pas en alerte`() = runTest {
        planifie("Levothyrox", stock = 90.0)

        assertFalse(observer().first().single().enAlerte)
    }

    @Test
    fun `un medicament a la demande n a ni date ni jours restants`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 4.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = emptyList(),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        val ligne = observer().first().single()

        assertTrue(ligne.aLaDemande)
        assertNull(ligne.dateEpuisement)
        assertNull(ligne.joursRestants)
        assertTrue(ligne.enAlerte, "4 unites sous un seuil de 5")
    }

    @Test
    fun `les unites restantes sont mises en forme`() = runTest {
        planifie("Levothyrox", stock = 9.5)

        assertEquals("9½ comprimés", observer().first().single().libelleUnites)
    }

    @Test
    fun `la projection de stock utilise la version en vigueur aujourd hui`() = runTest {
        // Version close hier a 1 comprime par jour, version courante a 4 par jour.
        // Avec 8 unites en stock, la bonne version epuise en 2 jours, pas en 8.
        val id = medicamentAvecDeuxVersions(doseAncienne = 1.0, doseCourante = 4.0, stock = 8.0)

        val ligne = observer().first().single { it.medicamentId == id }

        assertEquals(2L, ligne.joursRestants)
    }
}
