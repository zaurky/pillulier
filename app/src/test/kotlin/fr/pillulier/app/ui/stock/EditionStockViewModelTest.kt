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
