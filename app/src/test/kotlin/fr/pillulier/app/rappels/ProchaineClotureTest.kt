package fr.pillulier.app.rappels

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ProchaineClotureTest {

    @Test
    fun `avant l heure de cloture la prochaine est le jour meme`() {
        val maintenant = LocalDateTime.of(2026, 1, 5, 0, 1)

        assertEquals(LocalDateTime.of(2026, 1, 5, 0, 5), prochaineCloture(maintenant))
    }

    @Test
    fun `apres l heure de cloture la prochaine est le lendemain`() {
        val maintenant = LocalDateTime.of(2026, 1, 5, 22, 30)

        assertEquals(LocalDateTime.of(2026, 1, 6, 0, 5), prochaineCloture(maintenant))
    }

    @Test
    fun `a l heure de cloture pile la prochaine est le lendemain`() {
        val maintenant = LocalDateTime.of(2026, 1, 5, 0, 5)

        assertEquals(LocalDateTime.of(2026, 1, 6, 0, 5), prochaineCloture(maintenant))
    }

    @Test
    fun `le passage au mois suivant est correct`() {
        val maintenant = LocalDateTime.of(2026, 1, 31, 23, 59)

        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 5), prochaineCloture(maintenant))
    }
}
