package fr.pillulier.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StatutTest {

    private val prise = PriseAttendue(
        medicamentId = 10,
        moment = Moment.MATIN,
        dose = 1.0,
        heure = LocalDateTime.of(2026, 1, 5, 8, 0),
    )

    private fun evenement(
        medicamentId: Long = 10,
        date: LocalDate = LocalDate.of(2026, 1, 5),
        moment: Moment? = Moment.MATIN,
    ) = EvenementPrise(
        id = 1,
        medicamentId = medicamentId,
        date = date,
        moment = moment,
        doseReelle = 1.0,
        enregistreLe = Instant.parse("2026-01-05T07:55:00Z"),
    )

    @Test
    fun `a venir avant l heure du moment`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 7, 59))
        assertEquals(StatutPrise.A_VENIR, statut)
    }

    @Test
    fun `en retard a l heure exacte du moment`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 8, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `en retard tant que la journee court`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 23, 59))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `oubliee des que la journee est terminee`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 6, 0, 0))
        assertEquals(StatutPrise.OUBLIEE, statut)
    }

    @Test
    fun `prise si un evenement correspond`() {
        val statut = statut(prise, listOf(evenement()), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.PRISE, statut)
    }

    @Test
    fun `prise reste prise apres la fin de la journee`() {
        val statut = statut(prise, listOf(evenement()), LocalDateTime.of(2026, 1, 9, 9, 0))
        assertEquals(StatutPrise.PRISE, statut)
    }

    @Test
    fun `un evenement d un autre medicament ne compte pas`() {
        val statut = statut(prise, listOf(evenement(medicamentId = 99)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `un evenement d un autre moment ne compte pas`() {
        val statut = statut(prise, listOf(evenement(moment = Moment.SOIR)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `un evenement d un autre jour ne compte pas`() {
        val statut = statut(prise, listOf(evenement(date = LocalDate.of(2026, 1, 4))), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `une prise a la demande ne satisfait pas une prise planifiee`() {
        val statut = statut(prise, listOf(evenement(moment = null)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }
}
