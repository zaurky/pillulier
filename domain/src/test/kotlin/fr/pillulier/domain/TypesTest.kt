package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypesTest {
    @Test
    fun `les moments sont dans l ordre chronologique de la journee`() {
        assertEquals(
            listOf(Moment.MATIN, Moment.MIDI, Moment.SOIR, Moment.COUCHER),
            Moment.entries.toList(),
        )
    }

    @Test
    fun `une ordonnance planifiee expose son rythme et ses doses`() {
        val ordonnance = OrdonnanceAvecDoses(
            ordonnance = Ordonnance(
                id = 1,
                medicamentId = 7,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(
                DosePrescrite(Moment.MATIN, 2.0),
                DosePrescrite(Moment.SOIR, 0.5),
            ),
        )

        assertEquals(2.0, ordonnance.doses.first { it.moment == Moment.MATIN }.dose)
        assertEquals(0.5, ordonnance.doses.first { it.moment == Moment.SOIR }.dose)
        assertTrue(ordonnance.ordonnance.rythme is Rythme.JoursDeSemaine)
    }

    @Test
    fun `un medicament a la demande porte un seuil en unites et pas en jours`() {
        val medicament = Medicament(
            id = 3,
            nom = "Doliprane",
            dosage = "500 mg",
            forme = Forme.COMPRIME,
            unitesParBoite = 16,
            stockUnites = 12.0,
            seuilAlerteJours = null,
            seuilAlerteUnites = 5,
            critique = false,
        )

        assertEquals(null, medicament.seuilAlerteJours)
        assertEquals(5, medicament.seuilAlerteUnites)
    }
}
