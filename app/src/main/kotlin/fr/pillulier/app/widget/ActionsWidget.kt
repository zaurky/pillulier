package fr.pillulier.app.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import java.time.Instant

private const val ETIQUETTE = "ActionsWidget"

/** Dix secondes pour revenir sur une coche. */
const val SECONDES_ANNULATION = 10L

val CLE_MEDICAMENT = ActionParameters.Key<Long>("medicamentId")
val CLE_MOMENT = ActionParameters.Key<String>("moment")
val CLE_DOSE = ActionParameters.Key<Double>("dose")

val ETAT_MEDICAMENT = longPreferencesKey("annulable_medicament")
val ETAT_MOMENT = stringPreferencesKey("annulable_moment")
val ETAT_EXPIRATION = longPreferencesKey("annulable_expiration")

/** L'annulable tel qu'il est relu de l'état Glance, ou `null` s'il n'y en a pas. */
fun lireAnnulable(etat: Preferences): Annulable? {
    val medicamentId = etat[ETAT_MEDICAMENT] ?: return null
    val moment = etat[ETAT_MOMENT] ?: return null
    val expiration = etat[ETAT_EXPIRATION] ?: return null
    return Annulable(medicamentId, Moment.valueOf(moment), Instant.ofEpochMilli(expiration))
}

/**
 * Écrit ou efface l'annulable de l'état Glance. `internal` : la Task 5 doit
 * pouvoir refermer la fenêtre d'annulation sans dupliquer ces trois `remove`.
 */
internal suspend fun ecrireAnnulable(context: Context, glanceId: GlanceId, annulable: Annulable?) {
    updateAppWidgetState(context, glanceId) { etat ->
        if (annulable == null) {
            etat.remove(ETAT_MEDICAMENT)
            etat.remove(ETAT_MOMENT)
            etat.remove(ETAT_EXPIRATION)
        } else {
            etat[ETAT_MEDICAMENT] = annulable.medicamentId
            etat[ETAT_MOMENT] = annulable.moment.name
            etat[ETAT_EXPIRATION] = annulable.expiration.toEpochMilli()
        }
    }
}

/** Enregistre la prise, exactement comme la coche de l'écran Aujourd'hui. */
class ActionCocher : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medicamentId = parameters[CLE_MEDICAMENT] ?: return
        val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
        val dose = parameters[CLE_DOSE] ?: return
        val acces = acces(context)
        val horloge = acces.horloge()
        val jour = horloge.aujourdhui()

        try {
            acces.enregistrerPrise()(medicamentId, jour, moment, dose)
            // Le réarmement annule l'alarme mais pas la notification déjà
            // postée : celle d'une prise critique est `setOngoing`, donc
            // impossible à balayer.
            acces.notifications().retirer(CleRappel(medicamentId, jour, moment))
            acces.reArmerRappels()()
            ecrireAnnulable(
                context,
                glanceId,
                Annulable(medicamentId, moment, horloge.instant().plusSeconds(SECONDES_ANNULATION)),
            )
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            // Une exception non rattrapée ici tuerait le processus depuis
            // l'arrière-plan : on la rend visible sans faire tomber l'app.
            Log.e(ETIQUETTE, "coche impossible pour $medicamentId/$moment", erreur)
        }
    }
}

/** Efface la prise et rend le stock, puis fait revenir l'alarme. */
class ActionAnnuler : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medicamentId = parameters[CLE_MEDICAMENT] ?: return
        val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
        val acces = acces(context)
        val jour = acces.horloge().aujourdhui()

        try {
            acces.annulerPrise()(medicamentId, jour, moment)
            acces.reArmerRappels()()
            ecrireAnnulable(context, glanceId, annulable = null)
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            Log.e(ETIQUETTE, "annulation impossible pour $medicamentId/$moment", erreur)
        }
    }
}
