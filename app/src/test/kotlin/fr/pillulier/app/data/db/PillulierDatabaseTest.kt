package fr.pillulier.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillulierDatabaseTest {

    private lateinit var base: PillulierDatabase

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        )
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fermer() {
        base.close()
    }

    private fun medicament(nom: String = "Levothyrox", stock: Double = 30.0) = MedicamentEntity(
        nom = nom,
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = stock,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = true,
    )

    @Test
    fun `les quatre moments sont amorces a la creation de la base`() = runTest {
        val configs = base.moments().tous().associate { it.moment to it.heure }

        assertEquals(4, configs.size)
        assertEquals(LocalTime.of(8, 0), configs[Moment.MATIN])
        assertEquals(LocalTime.of(12, 0), configs[Moment.MIDI])
        assertEquals(LocalTime.of(19, 0), configs[Moment.SOIR])
        assertEquals(LocalTime.of(22, 0), configs[Moment.COUCHER])
    }

    @Test
    fun `un medicament fait l aller retour sans perdre ses champs`() = runTest {
        val id = base.medicaments().inserer(medicament())

        val relu = base.medicaments().parId(id)!!

        assertEquals("Levothyrox", relu.nom)
        assertEquals("75 µg", relu.dosage)
        assertEquals(Forme.COMPRIME, relu.forme)
        assertEquals(30.0, relu.stockUnites)
        assertEquals(7, relu.seuilAlerteJours)
        assertNull(relu.seuilAlerteUnites)
        assertTrue(relu.critique)
    }

    @Test
    fun `ajuster le stock l incremente et le decremente`() = runTest {
        val id = base.medicaments().inserer(medicament(stock = 10.0))

        base.medicaments().ajusterStock(id, -0.5)
        assertEquals(9.5, base.medicaments().parId(id)!!.stockUnites)

        base.medicaments().ajusterStock(id, 30.0)
        assertEquals(39.5, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `definir le stock ecrase la valeur`() = runTest {
        val id = base.medicaments().inserer(medicament(stock = 10.0))

        base.medicaments().definirStock(id, 42.0)

        assertEquals(42.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `une ordonnance revient avec ses doses`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val ordonnanceId = base.ordonnances().insererOrdonnance(
            OrdonnanceEntity(
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "TOUS_LES_JOURS",
                rythmeJours = null,
                rythmeN = null,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
        )
        base.ordonnances().insererDoses(
            listOf(
                DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.MATIN, dose = 1.0),
                DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.SOIR, dose = 0.5),
            ),
        )

        val relu = base.ordonnances().versionsDe(medicamentId).single()

        assertEquals(LocalDate.of(2026, 1, 1), relu.ordonnance.dateDebut)
        assertEquals(setOf(Moment.MATIN, Moment.SOIR), relu.doses.map { it.moment }.toSet())
        assertEquals(0.5, relu.doses.first { it.moment == Moment.SOIR }.dose)
    }

    @Test
    fun `supprimer les versions depuis une date supprime leurs doses en cascade`() = runTest {
        // `supprimerVersionsDepuis` est le seul chemin qui efface encore une
        // ligne `ordonnance` en base : `enregistrerVersion` l appelle a chaque
        // modification de prescription qui remplace une version plus tardive.
        val medicamentId = base.medicaments().inserer(medicament())
        val ordonnanceId = base.ordonnances().insererOrdonnance(
            OrdonnanceEntity(
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "TOUS_LES_JOURS",
                rythmeJours = null,
                rythmeN = null,
                dateDebut = LocalDate.of(2026, 1, 10),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 10),
            ),
        )
        base.ordonnances().insererDoses(
            listOf(DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.MATIN, dose = 1.0)),
        )

        base.ordonnances().supprimerVersionsDepuis(medicamentId, LocalDate.of(2026, 1, 10))

        assertTrue(base.ordonnances().versionsDe(medicamentId).isEmpty())
        assertEquals(0, base.ordonnances().comptePourOrdonnance(ordonnanceId))
    }

    @Test
    fun `enregistrer deux fois la meme prise planifiee est sans effet`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val prise = EvenementPriseEntity(
            medicamentId = medicamentId,
            date = LocalDate.of(2026, 1, 5),
            moment = Moment.MATIN,
            doseReelle = 1.0,
            enregistreLe = Instant.parse("2026-01-05T07:00:00Z"),
        )

        val premier = base.evenements().inserer(prise)
        val second = base.evenements().inserer(prise.copy(enregistreLe = Instant.parse("2026-01-05T08:00:00Z")))

        assertTrue(premier > 0)
        assertEquals(-1L, second, "la seconde insertion doit etre ignoree")
        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `plusieurs prises a la demande le meme jour sont autorisees`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val prise = EvenementPriseEntity(
            medicamentId = medicamentId,
            date = LocalDate.of(2026, 1, 5),
            moment = null,
            doseReelle = 1.0,
            enregistreLe = Instant.parse("2026-01-05T07:00:00Z"),
        )

        base.evenements().inserer(prise)
        base.evenements().inserer(prise.copy(enregistreLe = Instant.parse("2026-01-05T15:00:00Z")))

        assertEquals(2, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `les evenements se lisent par intervalle de dates`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        listOf(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)).forEach { date ->
            base.evenements().inserer(
                EvenementPriseEntity(
                    medicamentId = medicamentId,
                    date = date,
                    moment = Moment.MATIN,
                    doseReelle = 1.0,
                    enregistreLe = Instant.parse("2026-01-01T07:00:00Z"),
                ),
            )
        }

        val dansLaSemaine = base.evenements().entre(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11))

        assertEquals(listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)), dansLaSemaine.map { it.date })
    }
}
