package fr.pillulier.app.rappels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

const val EXTRA_MEDICAMENT_ID = "medicamentId"
const val EXTRA_DATE = "dateEpochDay"
const val EXTRA_MOMENT = "moment"
const val EXTRA_CRITIQUE = "critique"

class ProgrammateurAlarmesAndroid @Inject constructor(
    private val contexte: Context,
) : ProgrammateurAlarmes {

    private val gestionnaire = contexte.getSystemService(AlarmManager::class.java)

    override fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean) {
        val declenchement = quand.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        gestionnaire.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            declenchement,
            // Sans FLAG_NO_CREATE, l'intention en attente n'est jamais nulle.
            intentEnAttente(cle, critique, PendingIntent.FLAG_UPDATE_CURRENT)!!,
        )
    }

    override fun annuler(cle: CleRappel) {
        val existant = intentEnAttente(cle, critique = false, drapeaux = PendingIntent.FLAG_NO_CREATE)
        existant?.let {
            gestionnaire.cancel(it)
            it.cancel()
        }
    }

    private fun intentEnAttente(cle: CleRappel, critique: Boolean, drapeaux: Int): PendingIntent? {
        val intention = Intent(contexte, RecepteurRappel::class.java).apply {
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_CRITIQUE, critique)
        }

        return PendingIntent.getBroadcast(
            contexte,
            cle.codeRequete(),
            intention,
            drapeaux or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Reconstruit une clé depuis les extras d'une intention. */
fun cleDepuisIntent(intention: Intent): CleRappel = CleRappel(
    medicamentId = intention.getLongExtra(EXTRA_MEDICAMENT_ID, -1),
    date = LocalDate.ofEpochDay(intention.getLongExtra(EXTRA_DATE, 0)),
    moment = Moment.valueOf(requireNotNull(intention.getStringExtra(EXTRA_MOMENT))),
)
