package fr.pillulier.app.rappels

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.pillulier.app.temps.Horloge
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/** Aligne le travail quotidien sur 00h05. */
class PlanificateurQuotidien @Inject constructor(
    private val contexte: Context,
    private val horloge: Horloge,
) {
    fun planifier() {
        val maintenant = horloge.maintenant()
        val prochaine = maintenant.toLocalDate()
            .let { if (maintenant.toLocalTime() >= HEURE_CLOTURE) it.plusDays(1) else it }
            .atTime(HEURE_CLOTURE)

        val delai = Duration.between(maintenant, prochaine)

        WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
            TravailQuotidien.NOM,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TravailQuotidien>(Duration.ofDays(1))
                .setInitialDelay(delai)
                .build(),
        )
    }

    private companion object {
        val HEURE_CLOTURE: LocalTime = LocalTime.of(0, 5)
    }
}
