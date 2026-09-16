package fr.pillulier.app.widget

import fr.pillulier.domain.Moment
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `ActionAnnuler` doit revalider l'annulable persisté avant d'agir : ces cas
 * verrouillent la fonction pure qu'elle appelle pour cette décision, sans
 * passer par Glance.
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
}
