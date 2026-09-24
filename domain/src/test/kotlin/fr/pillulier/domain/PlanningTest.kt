package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2026-01-01 est un jeudi ; toutes les dates de ce test s'y réfèrent. */
class PlanningTest {

    private val heures = mapOf(
        Moment.MATIN to LocalTime.of(8, 0),
        Moment.MIDI to LocalTime.of(12, 0),
        Moment.SOIR to LocalTime.of(19, 0),
        Moment.COUCHER to LocalTime.of(22, 0),
    )

    private fun ordonnance(
        id: Long = 1,
        medicamentId: Long = 1,
        type: TypeOrdonnance = TypeOrdonnance.PLANIFIEE,
        rythme: Rythme = Rythme.TousLesJours,
        dateDebut: LocalDate = LocalDate.of(2026, 1, 1),
        dateFin: LocalDate? = null,
        dateAncrage: LocalDate = dateDebut,
    ) = Ordonnance(id, medicamentId, type, rythme, dateDebut, dateFin, dateAncrage)

    @Test
    fun `tous les jours retient chaque date de la fenetre`() {
        val o = ordonnance()
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun `jours de semaine ne retient que les jours listes`() {
        val o = ordonnance(rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)), "jeudi")
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 2)), "vendredi")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)), "lundi")
    }

    @Test
    fun `un jour sur N est compte depuis la date de debut`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2), dateDebut = LocalDate.of(2026, 1, 1))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 2)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 3)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 11)))
    }

    @Test
    fun `un jour sur trois est compte depuis la date de debut`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(3), dateDebut = LocalDate.of(2026, 1, 2))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 1)), "avant le debut")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 2)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 3)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 4)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `la fenetre de dates borne le rythme`() {
        val o = ordonnance(dateDebut = LocalDate.of(2026, 1, 5), dateFin = LocalDate.of(2026, 1, 7))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 4)), "veille du debut")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)), "premier jour de la cure")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 7)), "dernier jour de la cure")
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 8)), "lendemain de la fin")
    }

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

    @Test
    fun `une ordonnance a la demande n est jamais un jour actif`() {
        val o = ordonnance(type = TypeOrdonnance.A_LA_DEMANDE)
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `chaque dose prescrite produit une prise attendue horodatee`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(
                ordonnance = ordonnance(id = 1, medicamentId = 10),
                doses = listOf(DosePrescrite(Moment.MATIN, 2.0), DosePrescrite(Moment.SOIR, 0.5)),
            ),
        )

        val prises = prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures)

        assertEquals(
            listOf(
                PriseAttendue(10, Moment.MATIN, 2.0, LocalDateTime.of(2026, 1, 1, 8, 0)),
                PriseAttendue(10, Moment.SOIR, 0.5, LocalDateTime.of(2026, 1, 1, 19, 0)),
            ),
            prises,
        )
    }

    @Test
    fun `les prises de plusieurs medicaments sont triees par heure puis par medicament`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(ordonnance(id = 2, medicamentId = 20), listOf(DosePrescrite(Moment.SOIR, 1.0))),
            OrdonnanceAvecDoses(ordonnance(id = 1, medicamentId = 30), listOf(DosePrescrite(Moment.MATIN, 1.0))),
            OrdonnanceAvecDoses(ordonnance(id = 3, medicamentId = 10), listOf(DosePrescrite(Moment.MATIN, 1.0))),
        )

        val prises = prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures)

        assertEquals(listOf(10L, 30L, 20L), prises.map { it.medicamentId })
    }

    @Test
    fun `une ordonnance hors de son rythme ne produit aucune prise`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(
                ordonnance(rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY))),
                listOf(DosePrescrite(Moment.MATIN, 1.0)),
            ),
        )

        assertTrue(prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures).isEmpty())
    }

    @Test
    fun `une heure manquante est une erreur de programmation`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(ordonnance(), listOf(DosePrescrite(Moment.MATIN, 1.0))),
        )

        assertFailsWith<IllegalArgumentException> {
            prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, mapOf(Moment.MATIN to LocalTime.of(8, 0)))
        }
    }
}
