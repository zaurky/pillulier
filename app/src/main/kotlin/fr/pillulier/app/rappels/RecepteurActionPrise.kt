package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.widget.RafraichirWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETTE = "RecepteurActionPrise"

/** Traite les deux actions d'une notification de rappel : *Pris* et *Plus tard*. */
@AndroidEntryPoint
class RecepteurActionPrise : BroadcastReceiver() {

    @Inject lateinit var enregistrerPrise: EnregistrerPrise
    @Inject lateinit var medicaments: DepotMedicaments
    @Inject lateinit var preferences: DepotPreferences
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var programmateur: ProgrammateurAlarmes
    @Inject lateinit var horloge: Horloge
    @Inject lateinit var rafraichirWidget: RafraichirWidget

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val dose = intent.getDoubleExtra(EXTRA_DOSE, 0.0)
        val action = intent.action
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PRIS -> {
                        enregistrerPrise(cle.medicamentId, cle.date, cle.moment, dose)
                        programmateur.annuler(cle)
                        notifications.retirer(cle)
                        rafraichirWidget()
                    }

                    ACTION_PLUS_TARD -> {
                        val critique = medicaments.parId(cle.medicamentId)?.critique ?: false
                        val delai = preferences.instantane().delaiPlusTardMinutes
                        programmateur.programmer(
                            cle = cle,
                            quand = horloge.maintenant().plusMinutes(delai.toLong()),
                            critique = critique,
                        )
                        notifications.retirer(cle)
                        rafraichirWidget()
                    }
                }
            } catch (erreur: Throwable) {
                // Une exception non rattrapée ici tuerait le processus depuis
                // l'arrière-plan : on la rend visible sans faire tomber l'app.
                Log.e(ETIQUETTE, "action $action non traitée pour $cle", erreur)
            } finally {
                termine.finish()
            }
        }
    }
}
