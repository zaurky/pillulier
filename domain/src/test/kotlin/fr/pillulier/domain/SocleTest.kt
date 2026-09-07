package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SocleTest {
    @Test
    fun `java time est disponible dans le module domaine`() {
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 1, 4).dayOfWeek)
    }
}
