package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETTE = "RecepteurRappel"

/** Reçoit l'alarme d'un rappel et la confie à [DeclencherRappel]. */
@AndroidEntryPoint
class RecepteurRappel : BroadcastReceiver() {

    @Inject lateinit var declencherRappel: DeclencherRappel

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val critique = intent.getBooleanExtra(EXTRA_CRITIQUE, false)
        // Une alarme armée avant l'apparition du préavis n'a pas de phase :
        // elle sonne, comme avant.
        val phase = intent.getStringExtra(EXTRA_PHASE)?.let(Phase::valueOf) ?: Phase.SONNERIE
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                declencherRappel(cle, critique, phase)
            } catch (erreur: Throwable) {
                // Une exception non rattrapée ici tuerait le processus depuis
                // l'arrière-plan : on la rend visible sans faire tomber l'app.
                Log.e(ETIQUETTE, "rappel non posté pour $cle", erreur)
            } finally {
                termine.finish()
            }
        }
    }
}
