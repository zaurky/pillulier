package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETTE = "RecepteurDemarrage"

/**
 * Après un redémarrage ou une mise à jour de l'application, les alarmes exactes
 * sont perdues : on les reconstruit depuis le moteur, puisque rien n'y est une
 * vérité stockée.
 */
@AndroidEntryPoint
class RecepteurDemarrage : BroadcastReceiver() {

    @Inject lateinit var reArmerRappels: ReArmerRappels
    @Inject lateinit var planificateur: PlanificateurQuotidien
    @Inject lateinit var rafraichirWidget: RafraichirWidget

    override fun onReceive(context: Context, intent: Intent) {
        val reconnue = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!reconnue) return

        val termine = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reArmerRappels()
                planificateur.planifier()
                rafraichirWidget()
            } catch (erreur: Throwable) {
                // Une exception non rattrapée ici tuerait le processus depuis
                // l'arrière-plan : on la rend visible sans faire tomber l'app.
                Log.e(ETIQUETTE, "réarmement impossible après ${intent.action}", erreur)
            } finally {
                termine.finish()
            }
        }
    }
}
