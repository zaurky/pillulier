package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
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

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val critique = intent.getBooleanExtra(EXTRA_CRITIQUE, false)
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // « Aucune relance ne survit au lendemain » est un invariant du
                // rappel lui-même, pas une conséquence de la clôture de 00h05 :
                // le travail périodique est reportable, et une chaîne d'hier
                // relancerait sinon toutes les quinze minutes en pleine nuit.
                if (cle.date != horloge.aujourdhui()) return@launch

                val medicament = medicaments.parId(cle.medicamentId) ?: return@launch
                val dose = ordonnances.pourMedicament(cle.medicamentId)
                    ?.doses
                    ?.firstOrNull { it.moment == cle.moment }
                    ?.dose
                    ?: return@launch

                notifications.posterRappel(cle, medicament, dose, critique)

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
