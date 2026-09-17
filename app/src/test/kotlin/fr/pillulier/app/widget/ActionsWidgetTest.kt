package fr.pillulier.app.widget

import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les deux actions du widget revalident avant d'agir, parce qu'un widget peut
 * afficher des pixels figés depuis des heures : ces cas verrouillent les deux
 * fonctions pures qu'elles appellent pour ces décisions, sans passer par Glance.
 */
class ActionsWidgetTest {

    private val maintenant = Instant.parse("2026-01-05T08:05:00Z")
    private val aujourdhui = LocalDate.of(2026, 1, 5)

    private fun annulable(
        medicamentId: Long = 3L,
        moment: Moment = Moment.MATIN,
        date: LocalDate = aujourdhui,
        expiration: Instant = maintenant.plusSeconds(7),
    ) = Annulable(medicamentId, moment, date, expiration)

    @Test
    fun `aucun annulable n autorise rien`() {
        assertFalse(
            annulationAutorisee(
                annulable = null,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `un annulable qui correspond exactement autorise l annulation`() {
        assertTrue(
            annulationAutorisee(
                annulable = annulable(),
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `un annulable expire n autorise pas l annulation`() {
        val expire = annulable(expiration = maintenant.minusSeconds(1))

        assertFalse(
            annulationAutorisee(
                annulable = expire,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `un annulable d une autre date n autorise pas l annulation`() {
        val hier = annulable(date = aujourdhui.minusDays(1))

        assertFalse(
            annulationAutorisee(
                annulable = hier,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `un annulable qui designe un autre medicament n autorise pas l annulation`() {
        val autreMedicament = annulable(medicamentId = 99L)

        assertFalse(
            annulationAutorisee(
                annulable = autreMedicament,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `un annulable qui designe un autre moment n autorise pas l annulation`() {
        val autreMoment = annulable(moment = Moment.SOIR)

        assertFalse(
            annulationAutorisee(
                annulable = autreMoment,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    @Test
    fun `l expiration exacte n autorise plus l annulation`() {
        val pileExpire = annulable(expiration = maintenant)

        assertFalse(
            annulationAutorisee(
                annulable = pileExpire,
                medicamentId = 3L,
                moment = Moment.MATIN,
                date = aujourdhui,
                maintenant = maintenant,
            ),
        )
    }

    private fun attendue(
        medicamentId: Long = 3L,
        moment: Moment = Moment.MATIN,
        dose: Double = 1.0,
        statut: StatutPrise = StatutPrise.A_VENIR,
    ) = LigneJournee(
        medicamentId = medicamentId,
        nom = "Levothyrox",
        dosage = "75 µg",
        moment = moment,
        heure = LocalTime.of(8, 0),
        dose = dose,
        libelleDose = "1 comprimé",
        statut = statut,
    )

    @Test
    fun `une prise encore attendue est cochable`() {
        val trouvee = priseACocher(
            listOf(attendue(medicamentId = 3L, moment = Moment.MATIN)),
            medicamentId = 3L,
            moment = Moment.MATIN,
        )

        assertEquals(3L, trouvee?.medicamentId)
    }

    @Test
    fun `une prise en retard reste cochable`() {
        assertEquals(
            3L,
            priseACocher(
                listOf(attendue(statut = StatutPrise.EN_RETARD)),
                medicamentId = 3L,
                moment = Moment.MATIN,
            )?.medicamentId,
        )
    }

    @Test
    fun `une prise deja faite n est plus cochable`() {
        assertNull(
            priseACocher(
                listOf(attendue(statut = StatutPrise.PRISE)),
                medicamentId = 3L,
                moment = Moment.MATIN,
            ),
        )
    }

    @Test
    fun `une prise oubliee n est plus cochable`() {
        assertNull(
            priseACocher(
                listOf(attendue(statut = StatutPrise.OUBLIEE)),
                medicamentId = 3L,
                moment = Moment.MATIN,
            ),
        )
    }

    @Test
    fun `une prise qui n est plus au planning du jour n est pas cochable`() {
        assertNull(priseACocher(emptyList(), medicamentId = 3L, moment = Moment.MATIN))
    }

    @Test
    fun `le moment du clic doit correspondre`() {
        assertNull(
            priseACocher(
                listOf(attendue(moment = Moment.MATIN)),
                medicamentId = 3L,
                moment = Moment.SOIR,
            ),
        )
    }

    @Test
    fun `c est la dose courante qui est renvoyee et non celle du rendu`() {
        // L'ordonnance est passée à une demi-dose depuis que le widget a été
        // dessiné : c'est 0,5 qu'il faut décrémenter, pas le 1 gravé au rendu.
        val trouvee = priseACocher(
            listOf(attendue(dose = 0.5)),
            medicamentId = 3L,
            moment = Moment.MATIN,
        )

        assertEquals(0.5, trouvee?.dose)
    }
}
