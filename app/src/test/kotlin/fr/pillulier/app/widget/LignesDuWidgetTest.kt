package fr.pillulier.app.widget

import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LignesDuWidgetTest {

    private val maintenant = Instant.parse("2026-01-05T08:05:00Z")
    private val aujourdhui = LocalDate.of(2026, 1, 5)

    private fun ligne(
        id: Long,
        moment: Moment = Moment.MATIN,
        statut: StatutPrise,
    ) = LigneJournee(
        medicamentId = id,
        nom = "Levothyrox",
        dosage = "75 µg",
        moment = moment,
        heure = LocalTime.of(8, 0),
        dose = 1.0,
        libelleDose = "1 comprimé",
        statut = statut,
    )

    @Test
    fun `seules les prises restantes sont gardees`() {
        val lignes = listOf(
            ligne(1, statut = StatutPrise.EN_RETARD),
            ligne(2, statut = StatutPrise.A_VENIR),
            ligne(3, statut = StatutPrise.PRISE),
            ligne(4, statut = StatutPrise.OUBLIEE),
        )

        val gardees = lignesDuWidget(lignes, annulable = null, jour = aujourdhui, maintenant = maintenant)

        assertEquals(listOf(1L, 2L), gardees.map { it.medicamentId })
        assertTrue(gardees.all { it.date == aujourdhui }, "chaque ligne porte le jour rendu")
    }

    @Test
    fun `une prise en retard est marquee comme telle`() {
        val gardees = lignesDuWidget(
            listOf(ligne(1, statut = StatutPrise.EN_RETARD), ligne(2, statut = StatutPrise.A_VENIR)),
            annulable = null,
            jour = aujourdhui,
            maintenant = maintenant,
        )

        assertTrue(gardees.first { it.medicamentId == 1L }.enRetard)
        assertTrue(!gardees.first { it.medicamentId == 2L }.enRetard)
    }

    @Test
    fun `la prise annulable est reinjectee barree avec sa date`() {
        val lignes = listOf(ligne(3, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.MATIN, aujourdhui, maintenant.plusSeconds(7))

        val gardees = lignesDuWidget(lignes, annulable, aujourdhui, maintenant)

        assertEquals(1, gardees.size)
        assertTrue(gardees.single().barree)
        assertEquals(aujourdhui, gardees.single().date)
    }

    @Test
    fun `un annulable expire est ignore`() {
        val lignes = listOf(ligne(3, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.MATIN, aujourdhui, maintenant.minusSeconds(1))

        assertTrue(lignesDuWidget(lignes, annulable, aujourdhui, maintenant).isEmpty())
    }

    @Test
    fun `un annulable qui designe une autre prise ne reinjecte rien`() {
        val lignes = listOf(ligne(3, moment = Moment.MATIN, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.SOIR, aujourdhui, maintenant.plusSeconds(7))

        assertTrue(lignesDuWidget(lignes, annulable, aujourdhui, maintenant).isEmpty())
    }

    @Test
    fun `une ligne non barree porte elle aussi le jour rendu`() {
        val gardees = lignesDuWidget(
            listOf(ligne(1, statut = StatutPrise.EN_RETARD)),
            annulable = null,
            jour = aujourdhui,
            maintenant = maintenant,
        )

        assertEquals(aujourdhui, gardees.single().date)
    }

    @Test
    fun `une journee sans prise donne une liste vide`() {
        assertTrue(lignesDuWidget(emptyList(), annulable = null, jour = aujourdhui, maintenant = maintenant).isEmpty())
    }

    @Test
    fun `une ligne barree garde la date de l annulable meme apres minuit`() {
        // La session a commencé hier ; il est minuit passé et la fenêtre de dix
        // secondes court encore. L'annulation doit désigner la prise d'hier,
        // pas celle du jour qui vient de commencer.
        val hier = aujourdhui.minusDays(1)
        val lignes = listOf(ligne(3, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.MATIN, hier, maintenant.plusSeconds(7))

        val gardees = lignesDuWidget(lignes, annulable, aujourdhui, maintenant)

        assertEquals(hier, gardees.single().date)
    }
}
