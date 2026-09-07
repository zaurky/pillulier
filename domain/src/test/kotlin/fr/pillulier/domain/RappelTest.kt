package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RappelTest {

    @Test
    fun `le code de requete est deterministe`() {
        val cle = CleRappel(42, LocalDate.of(2026, 1, 5), Moment.SOIR)
        assertEquals(cle.codeRequete(), CleRappel(42, LocalDate.of(2026, 1, 5), Moment.SOIR).codeRequete())
    }

    @Test
    fun `le code de requete est toujours positif`() {
        val cle = CleRappel(1, LocalDate.of(1970, 1, 1), Moment.MATIN)
        assertTrue(cle.codeRequete() >= 0)
        assertTrue(CleRappel(999, LocalDate.of(2030, 12, 31), Moment.COUCHER).codeRequete() >= 0)
    }

    @Test
    fun `les codes sont distincts sur une fenetre realiste`() {
        val debut = LocalDate.of(2026, 1, 1)
        val codes = buildList {
            (0..3L).forEach { decalage ->
                (1L..200L).forEach { medicamentId ->
                    Moment.entries.forEach { moment ->
                        add(CleRappel(medicamentId, debut.plusDays(decalage), moment).codeRequete())
                    }
                }
            }
        }

        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `changer un seul composant change le code`() {
        val base = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        assertTrue(base.codeRequete() != base.copy(medicamentId = 8).codeRequete())
        assertTrue(base.codeRequete() != base.copy(moment = Moment.MIDI).codeRequete())
        assertTrue(base.codeRequete() != base.copy(date = LocalDate.of(2026, 1, 6)).codeRequete())
    }
}
