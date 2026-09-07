package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            } finally {
                termine.finish()
            }
        }
    }
}
