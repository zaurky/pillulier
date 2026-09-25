package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.usecase.ClotureQuotidienne
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETTE = "RecepteurQuotidien"

/**
 * Reveille par l'alarme exacte de minuit. Il reprogramme celle du lendemain
 * avant tout traitement : c'est cette reprogrammation qui fait la chaine, et la
 * differer jusqu'apres le travail la perdrait si celui-ci echouait.
 */
@AndroidEntryPoint
class RecepteurQuotidien : BroadcastReceiver() {

    @Inject lateinit var clotureQuotidienne: ClotureQuotidienne
    @Inject lateinit var planificateur: PlanificateurQuotidien

    override fun onReceive(context: Context, intent: Intent) {
        planificateur.planifier()

        val termine = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                clotureQuotidienne()
            } catch (erreur: Throwable) {
                // Une exception non rattrapee ici tuerait le processus depuis
                // l'arriere-plan : on la rend visible sans faire tomber l'app.
                Log.e(ETIQUETTE, "cloture quotidienne impossible", erreur)
            } finally {
                termine.finish()
            }
        }
    }
}
