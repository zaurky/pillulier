package fr.pillulier.app.ui.semaine

import fr.pillulier.app.usecase.EntreeSemaine
import fr.pillulier.app.usecase.EtatSemaine
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LignesDuJourTest {

    private val lundi = LocalDate.of(2026, 1, 5)
    private val mardi = LocalDate.of(2026, 1, 6)

    private fun entree(nom: String, statut: StatutPrise = StatutPrise.PRISE) =
        EntreeSemaine(medicamentId = 1, nom = nom, libelleDose = "1 comprime", statut = statut)

    @Test
    fun `un moment sans prise n apparait pas`() {
        val etat = EtatSemaine(
            jours = listOf(lundi),
            cellules = mapOf(
                (lundi to Moment.MATIN) to listOf(entree("colchicine")),
                (lundi to Moment.SOIR) to listOf(entree("colchicine")),
            ),
        )

        assertEquals(listOf(Moment.MATIN, Moment.SOIR), lignesDuJour(etat, lundi).map { it.moment })
    }

    @Test
    fun `les moments suivent l ordre de la journee`() {
        val etat = EtatSemaine(
            jours = listOf(lundi),
            cellules = mapOf(
                (lundi to Moment.COUCHER) to listOf(entree("melatonine")),
                (lundi to Moment.MATIN) to listOf(entree("colchicine")),
                (lundi to Moment.MIDI) to listOf(entree("doliprane")),
            ),
        )

        assertEquals(
            listOf(Moment.MATIN, Moment.MIDI, Moment.COUCHER),
            lignesDuJour(etat, lundi).map { it.moment },
        )
    }

    @Test
    fun `un moment dont la liste est vide n apparait pas`() {
        val etat = EtatSemaine(
            jours = listOf(lundi),
            cellules = mapOf(
                (lundi to Moment.MATIN) to listOf(entree("colchicine")),
                (lundi to Moment.MIDI) to emptyList(),
            ),
        )

        assertEquals(listOf(Moment.MATIN), lignesDuJour(etat, lundi).map { it.moment })
    }

    @Test
    fun `un jour sans aucune prise rend une liste vide`() {
        val etat = EtatSemaine(
            jours = listOf(lundi, mardi),
            cellules = mapOf((lundi to Moment.MATIN) to listOf(entree("colchicine"))),
        )

        assertTrue(lignesDuJour(etat, mardi).isEmpty())
    }

    @Test
    fun `chaque ligne porte les entrees de son moment`() {
        val etat = EtatSemaine(
            jours = listOf(lundi),
            cellules = mapOf(
                (lundi to Moment.MATIN) to listOf(entree("kineret"), entree("colchicine")),
            ),
        )

        assertEquals(listOf("kineret", "colchicine"), lignesDuJour(etat, lundi).single().entrees.map { it.nom })
    }
}
