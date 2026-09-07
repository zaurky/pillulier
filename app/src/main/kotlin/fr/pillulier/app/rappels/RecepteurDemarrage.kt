package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.usecase.ReArmerRappels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Après un redémarrage, les alarmes exactes sont perdues : on les reconstruit
 * depuis le moteur, puisque rien n'y est une vérité stockée.
 */
@AndroidEntryPoint
class RecepteurDemarrage : BroadcastReceiver() {

    @Inject lateinit var reArmerRappels: ReArmerRappels
    @Inject lateinit var planificateur: PlanificateurQuotidien

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val termine = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reArmerRappels()
                planificateur.planifier()
            } finally {
                termine.finish()
            }
        }
    }
}
