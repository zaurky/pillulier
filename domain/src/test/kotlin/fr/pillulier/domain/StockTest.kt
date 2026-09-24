package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockTest {

    private val debut = LocalDate.of(2026, 1, 1)

    private fun ordonnance(
        rythme: Rythme = Rythme.TousLesJours,
        dateFin: LocalDate? = null,
        doses: List<DosePrescrite> = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        dateAncrage: LocalDate = debut,
    ) = OrdonnanceAvecDoses(
        ordonnance = Ordonnance(1, 10, TypeOrdonnance.PLANIFIEE, rythme, debut, dateFin, dateAncrage),
        doses = doses,
    )

    @Test
    fun `la consommation d un jour est la somme des doses du jour`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 2.0), DosePrescrite(Moment.SOIR, 0.5)))
        assertEquals(2.5, consommationDuJour(o, debut))
    }

    @Test
    fun `la consommation est nulle un jour non retenu par le rythme`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2))
        assertEquals(0.0, consommationDuJour(o, LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `dix comprimes a un par jour s epuisent le onzieme jour`() {
        assertEquals(
            LocalDate.of(2026, 1, 11),
            projectionStock(stockUnites = 10.0, ordonnance = ordonnance(), depuis = debut),
        )
    }

    @Test
    fun `les demi doses sont comptees exactement`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 0.5), DosePrescrite(Moment.SOIR, 0.5)))
        assertEquals(LocalDate.of(2026, 1, 11), projectionStock(10.0, o, debut))
    }

    @Test
    fun `un rythme irregulier etire la date d epuisement`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2))
        // Consommation les 1, 3, 5, 7 et 9 janvier ; le 11 ne peut plus etre servi.
        assertEquals(LocalDate.of(2026, 1, 11), projectionStock(5.0, o, debut))
    }

    @Test
    fun `une cure qui s arrete avant l epuisement n a pas de rupture`() {
        val o = ordonnance(dateFin = LocalDate.of(2026, 1, 5))
        assertNull(projectionStock(10.0, o, debut))
    }

    @Test
    fun `un stock vide s epuise le jour meme`() {
        assertEquals(debut, projectionStock(0.0, ordonnance(), debut))
    }

    @Test
    fun `un stock insuffisant pour une journee complete s epuise ce jour la`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 2.0)))
        assertEquals(LocalDate.of(2026, 1, 2), projectionStock(3.0, o, debut))
    }

    @Test
    fun `au dela de l horizon il n y a pas de rupture a annoncer`() {
        assertNull(projectionStock(10_000.0, ordonnance(), debut))
    }

    @Test
    fun `l horizon est configurable`() {
        assertNull(projectionStock(30.0, ordonnance(), debut, horizonJours = 10))
    }
}
