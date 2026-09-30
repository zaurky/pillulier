package fr.pillulier.app.rappels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import fr.pillulier.app.debug.JournalDebug // JOURNAL-DEBUG
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
        JournalDebug.ecrire("ALARME", "posee $cle pour $quand critique=$critique") // JOURNAL-DEBUG
        val declenchement = quand.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intention = creerIntentEnAttente(cle, critique)

        // Une autorisation d'alarme exacte retirée doit dégrader la ponctualité
        // du rappel, pas casser l'application : `setExactAndAllowWhileIdle`
        // lèverait une `SecurityException` au beau milieu d'un réarmement.
        if (peutProgrammerExactement()) {
            gestionnaire.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                declenchement,
                intention,
            )
        } else {
            gestionnaire.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                declenchement,
                intention,
            )
        }
    }

    private fun peutProgrammerExactement(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gestionnaire.canScheduleExactAlarms()

    override fun annuler(cle: CleRappel) {
        rechercherIntentEnAttente(cle)?.let {
            gestionnaire.cancel(it)
            it.cancel()
            // JOURNAL-DEBUG : seules les annulations qui trouvent quelque chose
            // sont tracees — `ReArmerRappels` en tente des dizaines a vide.
            JournalDebug.ecrire("ALARME", "retiree $cle")
        }
    }

    /** Crée ou remplace l'intention en attente du rappel : jamais nulle. */
    private fun creerIntentEnAttente(cle: CleRappel, critique: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            contexte,
            cle.codeRequete(),
            intentionRappel(cle, critique),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Retrouve l'intention en attente sans la créer : nulle quand aucune alarme
     * n'est armée pour cette clé. L'égalité des `PendingIntent` ignore les extras,
     * donc le drapeau critique passé ici est sans importance.
     */
    private fun rechercherIntentEnAttente(cle: CleRappel): PendingIntent? =
        PendingIntent.getBroadcast(
            contexte,
            cle.codeRequete(),
            intentionRappel(cle, critique = false),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun intentionRappel(cle: CleRappel, critique: Boolean): Intent =
        Intent(contexte, RecepteurRappel::class.java).apply {
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_CRITIQUE, critique)
        }
}

/** Reconstruit une clé depuis les extras d'une intention. */
fun cleDepuisIntent(intention: Intent): CleRappel = CleRappel(
    medicamentId = intention.getLongExtra(EXTRA_MEDICAMENT_ID, -1),
    date = LocalDate.ofEpochDay(intention.getLongExtra(EXTRA_DATE, 0)),
    moment = Moment.valueOf(requireNotNull(intention.getStringExtra(EXTRA_MOMENT))),
)
