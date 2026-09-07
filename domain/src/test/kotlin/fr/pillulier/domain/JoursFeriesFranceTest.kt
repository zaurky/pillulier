package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class JoursFeriesFranceTest {

    @Test
    fun `paques est calculee par le comput gregorien`() {
        assertEquals(LocalDate.of(2024, 3, 31), JoursFeriesFrance.paques(2024))
        assertEquals(LocalDate.of(2025, 4, 20), JoursFeriesFrance.paques(2025))
        assertEquals(LocalDate.of(2026, 4, 5), JoursFeriesFrance.paques(2026))
        assertEquals(LocalDate.of(2027, 3, 28), JoursFeriesFrance.paques(2027))
    }

    @Test
    fun `paques precoce et annee bissextile`() {
        assertEquals(LocalDate.of(2008, 3, 23), JoursFeriesFrance.paques(2008))
        assertEquals(LocalDate.of(2016, 3, 27), JoursFeriesFrance.paques(2016))
    }

    @Test
    fun `les huit jours fixes sont feries`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 8),
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 12, 25),
        ).forEach { assertTrue(JoursFeriesFrance.estFerie(it), "$it devrait etre ferie") }
    }

    @Test
    fun `les trois jours mobiles de 2026 sont feries`() {
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 4, 6)), "lundi de Paques")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 5, 14)), "Ascension")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 5, 25)), "lundi de Pentecote")
    }

    @Test
    fun `les trois jours mobiles de 2024 sont feries`() {
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 4, 1)), "lundi de Paques")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 5, 9)), "Ascension")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 5, 20)), "lundi de Pentecote")
    }

    @Test
    fun `un jour ordinaire n est pas ferie`() {
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 3, 17)))
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 4, 5)), "Paques est un dimanche, pas un jour ferie legal distinct a tester ici")
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 12, 26)))
    }

    @Test
    fun `il y a exactement onze jours feries par an`() {
        listOf(2024, 2025, 2026, 2027).forEach { annee ->
            val compte = (1..LocalDate.of(annee, 12, 31).dayOfYear)
                .map { LocalDate.ofYearDay(annee, it) }
                .count { JoursFeriesFrance.estFerie(it) }
            assertEquals(11, compte, "annee $annee")
        }
    }
}
