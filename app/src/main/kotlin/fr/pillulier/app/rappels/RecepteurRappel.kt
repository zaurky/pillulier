package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.debug.JournalDebug // JOURNAL-DEBUG
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.RappelEncoreDu
import fr.pillulier.app.widget.RafraichirWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETTE = "RecepteurRappel"

/**
 * Poste la notification du médicament attendu, puis arme immédiatement la
 * relance suivante. La chaîne s'interrompt quand la prise est enregistrée ou à
 * la clôture de la journée, qui annule la clé.
 */
@AndroidEntryPoint
class RecepteurRappel : BroadcastReceiver() {

    @Inject lateinit var medicaments: DepotMedicaments
    @Inject lateinit var ordonnances: DepotOrdonnances
    @Inject lateinit var preferences: DepotPreferences
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var programmateur: ProgrammateurAlarmes
    @Inject lateinit var horloge: Horloge
    @Inject lateinit var rappelEncoreDu: RappelEncoreDu
    @Inject lateinit var rafraichirWidget: RafraichirWidget

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val critique = intent.getBooleanExtra(EXTRA_CRITIQUE, false)
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // L'alarme qui nous réveille peut être partie juste avant que
                // la prise soit cochée : `annuler` n'a aucune prise sur un
                // déclenchement en vol. C'est le journal qui tranche, jamais
                // l'alarme.
                if (!rappelEncoreDu(cle)) {
                    // JOURNAL-DEBUG : une alarme en vol refusee ici, c'est
                    // exactement le bug 2 attrape au vol.
                    JournalDebug.ecrire("RAPPEL", "refuse $cle (deja pris ou jour passe)")
                    return@launch
                }

                val medicament = medicaments.parId(cle.medicamentId) ?: return@launch
                val dose = ordonnances.enVigueur(cle.medicamentId, cle.date)
                    ?.doses
                    ?.firstOrNull { it.moment == cle.moment }
                    ?.dose
                    ?: return@launch

                JournalDebug.ecrire("RAPPEL", "notification postee ${medicament.nom} $cle") // JOURNAL-DEBUG
                notifications.posterRappel(cle, medicament, dose, critique)
                rafraichirWidget()

                val intervalle = preferences.instantane().intervalleRelanceMinutes
                programmateur.programmer(
                    cle = cle,
                    quand = horloge.maintenant().plusMinutes(intervalle.toLong()),
                    critique = critique,
                )
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
