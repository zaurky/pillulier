package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repères : le 2026-01-01 est un jeudi, le 2026-01-11 un dimanche,
 * le 2026-05-01 un vendredi (férié), le 2026-05-08 un vendredi (férié).
 */
class DateAlerteTest {

    private val ferie = JoursFeriesFrance::estFerie

    @Test
    fun `sans jour ferme la fenetre l alerte tombe au seuil exact`() {
        assertEquals(
            LocalDate.of(2026, 1, 12),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 3, estFerie = ferie),
        )
    }

    @Test
    fun `un dimanche dans la fenetre recule l alerte d un jour`() {
        assertEquals(
            LocalDate.of(2026, 1, 7),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 7, estFerie = ferie),
        )
    }

    @Test
    fun `un ferie dans la fenetre recule l alerte d un jour`() {
        assertEquals(
            LocalDate.of(2026, 4, 30),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 5, 2), seuilJours = 1, estFerie = ferie),
        )
    }

    @Test
    fun `plusieurs jours fermes n ajoutent qu un seul jour de marge`() {
        assertEquals(
            LocalDate.of(2026, 5, 3),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 5, 11), seuilJours = 7, estFerie = ferie),
        )
    }

    @Test
    fun `le jour d epuisement fait partie de la fenetre`() {
        assertEquals(
            LocalDate.of(2026, 1, 10),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 11), seuilJours = 0, estFerie = ferie),
        )
    }

    @Test
    fun `un seuil nul sans jour ferme laisse l alerte le jour de l epuisement`() {
        assertEquals(
            LocalDate.of(2026, 1, 15),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 0, estFerie = ferie),
        )
    }
}
