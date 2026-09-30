package fr.pillulier.app.widget

import fr.pillulier.app.debug.JournalDebug // JOURNAL-DEBUG
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private const val ETIQUETTE = "ActionsWidget"

/** Dix secondes pour revenir sur une coche. */
const val SECONDES_ANNULATION = 10L

val CLE_MEDICAMENT = ActionParameters.Key<Long>("medicamentId")
val CLE_MOMENT = ActionParameters.Key<String>("moment")
val CLE_DATE = ActionParameters.Key<String>("date")

val ETAT_MEDICAMENT = longPreferencesKey("annulable_medicament")
val ETAT_MOMENT = stringPreferencesKey("annulable_moment")
val ETAT_DATE = stringPreferencesKey("annulable_date")
val ETAT_EXPIRATION = longPreferencesKey("annulable_expiration")

/** L'annulable tel qu'il est relu de l'état Glance, ou `null` s'il n'y en a pas. */
fun lireAnnulable(etat: Preferences): Annulable? {
    val medicamentId = etat[ETAT_MEDICAMENT] ?: return null
    val moment = etat[ETAT_MOMENT] ?: return null
    val date = etat[ETAT_DATE] ?: return null
    val expiration = etat[ETAT_EXPIRATION] ?: return null
    return Annulable(medicamentId, Moment.valueOf(moment), LocalDate.parse(date), Instant.ofEpochMilli(expiration))
}

/**
 * Écrit ou efface l'annulable de l'état Glance. `internal` : la Task 5 doit
 * pouvoir refermer la fenêtre d'annulation sans dupliquer ces quatre `remove`.
 */
internal suspend fun ecrireAnnulable(context: Context, glanceId: GlanceId, annulable: Annulable?) {
    updateAppWidgetState(context, glanceId) { etat ->
        if (annulable == null) {
            etat.remove(ETAT_MEDICAMENT)
            etat.remove(ETAT_MOMENT)
            etat.remove(ETAT_DATE)
            etat.remove(ETAT_EXPIRATION)
        } else {
            etat[ETAT_MEDICAMENT] = annulable.medicamentId
            etat[ETAT_MOMENT] = annulable.moment.name
            etat[ETAT_DATE] = annulable.date.toString()
            etat[ETAT_EXPIRATION] = annulable.expiration.toEpochMilli()
        }
    }
}

/**
 * Vrai si l'annulable relu de l'état Glance est bien celui que ce clic cible :
 * même prise, même jour, et fenêtre pas encore expirée. C'est la même vérité
 * qu'au rendu (`lignesDuWidget`) et à l'action (`ActionAnnuler`) — sans elle,
 * un clic sur une ligne barrée figée par une session Glance déjà morte
 * pourrait annuler une prise qui n'est plus celle affichée à l'écran.
 */
fun annulationAutorisee(
    annulable: Annulable?,
    medicamentId: Long,
    moment: Moment,
    date: LocalDate,
    maintenant: Instant,
): Boolean {
    if (annulable == null) return false
    return annulable.medicamentId == medicamentId &&
        annulable.moment == moment &&
        annulable.date == date &&
        annulable.expiration.isAfter(maintenant)
}

/**
 * La prise que ce clic cible, si elle est encore attendue aujourd'hui — sinon
 * `null`. Les pixels d'un widget peuvent dater de plusieurs heures : le couple
 * coché peut avoir disparu du planning, ou changé de dose, depuis le rendu.
 * C'est `prisesAttendues`, relu à l'instant du clic, qui tranche.
 */
fun priseACocher(
    lignes: List<LigneJournee>,
    medicamentId: Long,
    moment: Moment,
): LigneJournee? = lignes.firstOrNull { ligne ->
    ligne.medicamentId == medicamentId &&
        ligne.moment == moment &&
        (ligne.statut == StatutPrise.A_VENIR || ligne.statut == StatutPrise.EN_RETARD)
}

/** Enregistre la prise, exactement comme la coche de l'écran Aujourd'hui — après revalidation. */
class ActionCocher : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        try {
            val medicamentId = parameters[CLE_MEDICAMENT] ?: return
            val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
            val rendueLe = parameters[CLE_DATE]?.let { LocalDate.parse(it) } ?: return
            val acces = acces(context)
            val horloge = acces.horloge()
            val jour = horloge.aujourdhui()

            // La session Glance qui a dessiné cette case a pu mourir depuis.
            // Un rendu d'hier cocherait sinon une prise du jour courant, pour
            // un couple qui n'est peut-être plus au planning.
            if (rendueLe != jour) {
                PillulierWidget.update(context, glanceId)
                return
            }

            // La dose vient du planning relu maintenant, jamais de celle gravée
            // au rendu : une ordonnance modifiée depuis décrémenterait faux.
            val prise = priseACocher(acces.observerJournee()(jour).first(), medicamentId, moment)
            if (prise == null) {
                PillulierWidget.update(context, glanceId)
                return
            }

            // JOURNAL-DEBUG
            JournalDebug.ecrire("COCHE", "widget: ${prise.nom} $moment le $jour")
            acces.enregistrerPrise()(medicamentId, jour, moment, prise.dose)
            // Écrite tout de suite après l'enregistrement : sinon la ligne
            // disparaît puis revient barrée, et la fenêtre de dix secondes
            // démarre en retard sur ce qu'affiche déjà l'écran.
            ecrireAnnulable(
                context,
                glanceId,
                Annulable(medicamentId, moment, jour, horloge.instant().plusSeconds(SECONDES_ANNULATION)),
            )
            // Le réarmement annule l'alarme mais pas la notification déjà
            // postée : celle d'une prise critique est `setOngoing`, donc
            // impossible à balayer.
            acces.notifications().retirer(CleRappel(medicamentId, jour, moment))
            acces.reArmerRappels()()
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            // Une exception non rattrapée ici tuerait le processus depuis
            // l'arrière-plan : on la rend visible sans faire tomber l'app.
            Log.e(ETIQUETTE, "coche impossible pour ${parameters[CLE_MEDICAMENT]}/${parameters[CLE_MOMENT]}", erreur)
        }
    }
}

/** Efface la prise et rend le stock, puis fait revenir l'alarme — après revalidation. */
class ActionAnnuler : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        try {
            val medicamentId = parameters[CLE_MEDICAMENT] ?: return
            val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
            val date = parameters[CLE_DATE]?.let { LocalDate.parse(it) } ?: return
            val acces = acces(context)
            val horloge = acces.horloge()

            // La session Glance qui a rendu ce lien a pu mourir depuis : on
            // relit l'état réel plutôt que de faire confiance aux pixels
            // affichés, potentiellement figés depuis des heures.
            val etat = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val annulable = lireAnnulable(etat)

            if (!annulationAutorisee(annulable, medicamentId, moment, date, horloge.instant())) {
                // Rien à annuler, ou plus la même prise : on se contente de
                // redessiner le widget avec l'état réel, sans toucher à la base.
                PillulierWidget.update(context, glanceId)
                return
            }

            JournalDebug.ecrire("COCHE", "widget: annulation $medicamentId $moment le $date") // JOURNAL-DEBUG
            acces.annulerPrise()(medicamentId, date, moment)
            acces.reArmerRappels()()
            ecrireAnnulable(context, glanceId, annulable = null)
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            Log.e(ETIQUETTE, "annulation impossible pour ${parameters[CLE_MEDICAMENT]}/${parameters[CLE_MOMENT]}", erreur)
        }
    }
}
