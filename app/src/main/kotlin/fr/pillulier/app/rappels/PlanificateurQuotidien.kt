package fr.pillulier.app.rappels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.pillulier.app.temps.Horloge
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Deux declencheurs pour le meme travail de minuit, parce qu'aucun ne suffit
 * seul : l'alarme exacte est ponctuelle mais disparait a l'arret force, le
 * travail periodique survit mais que Doze peut differer de plusieurs heures —
 * bien au-dela de la premiere prise du matin, ce qui laissait le widget sur la
 * veille. [fr.pillulier.app.usecase.ClotureQuotidienne] est idempotente : les
 * voir passer tous les deux ne coute rien.
 */
class PlanificateurQuotidien @Inject constructor(
    private val contexte: Context,
    private val horloge: Horloge,
) {
    private val gestionnaire = contexte.getSystemService(AlarmManager::class.java)

    fun planifier() {
        val prochaine = prochaineCloture(horloge.maintenant())

        programmerAlarme(prochaine)
        programmerFilet(Duration.between(horloge.maintenant(), prochaine))
    }

    private fun programmerAlarme(prochaine: LocalDateTime) {
        val declenchement = prochaine.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intention = PendingIntent.getBroadcast(
            contexte,
            CODE_REQUETE,
            Intent(contexte, RecepteurQuotidien::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Meme prudence que ProgrammateurAlarmesAndroid : une autorisation
        // d'alarme exacte retiree doit degrader la ponctualite, pas lever une
        // SecurityException au demarrage de l'application.
        if (peutProgrammerExactement()) {
            gestionnaire.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, declenchement, intention)
        } else {
            gestionnaire.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, declenchement, intention)
        }
    }

    private fun programmerFilet(delai: Duration) {
        WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
            TravailQuotidien.NOM,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TravailQuotidien>(Duration.ofDays(1))
                .setInitialDelay(delai)
                .build(),
        )
    }

    private fun peutProgrammerExactement(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gestionnaire.canScheduleExactAlarms()

    private companion object {
        /**
         * Hors de la plage de `CleRappel.codeRequete()`, qui reste sous quatre
         * millions : aucune collision avec les alarmes de prise.
         */
        const val CODE_REQUETE = Int.MAX_VALUE
    }
}
