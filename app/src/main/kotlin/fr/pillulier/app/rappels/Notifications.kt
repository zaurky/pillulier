package fr.pillulier.app.rappels

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import fr.pillulier.app.MainActivity
import fr.pillulier.app.R
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import javax.inject.Inject

const val CANAL_RAPPELS = "rappels"
const val CANAL_CRITIQUES = "critiques"

const val ACTION_PRIS = "fr.pillulier.app.PRIS"
const val ACTION_PLUS_TARD = "fr.pillulier.app.PLUS_TARD"
const val EXTRA_DOSE = "dose"

/** Décalage d'identifiant des notifications de groupe, hors de la plage des clés. */
private const val BASE_GROUPE = 9_000_000

class Notifications @Inject constructor(private val contexte: Context) {

    private val gestionnaire = contexte.getSystemService(NotificationManager::class.java)

    fun creerCanaux() {
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_RAPPELS, "Rappels de prise", NotificationManager.IMPORTANCE_HIGH),
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_CRITIQUES, "Prises critiques", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
            },
        )
    }

    /**
     * Une notification par médicament, regroupée sous un résumé par moment : on
     * peut valider les comprimés et laisser la piqûre en attente.
     */
    fun posterRappel(cle: CleRappel, medicament: Medicament, dose: Double, critique: Boolean) {
        val notification = NotificationCompat.Builder(
            contexte,
            if (critique) CANAL_CRITIQUES else CANAL_RAPPELS,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${medicament.nom} ${medicament.dosage}")
            .setContentText("${libelleMoment(cle.moment)} — ${libelleDoseComplete(dose, medicament.forme)}")
            .setContentIntent(intentOuvertureApp())
            .setGroup(cleGroupe(cle.moment))
            .setAutoCancel(false)
            .setOngoing(critique)
            .addAction(0, "Pris", intentAction(ACTION_PRIS, cle, dose))
            .addAction(0, "Plus tard", intentAction(ACTION_PLUS_TARD, cle, dose))
            .apply { if (critique) setFullScreenIntent(intentAlarme(cle, dose), true) }
            .build()

        gestionnaire.notify(cle.codeRequete(), notification)
        gestionnaire.notify(BASE_GROUPE + cle.moment.ordinal, resumeDeGroupe(cle.moment))
    }

    fun retirer(cle: CleRappel) {
        gestionnaire.cancel(cle.codeRequete())
    }

    /** Retire toutes les notifications d'un moment, résumé compris. */
    fun retirerGroupe(moment: Moment) {
        gestionnaire.cancel(BASE_GROUPE + moment.ordinal)
    }

    private fun resumeDeGroupe(moment: Moment) = NotificationCompat.Builder(contexte, CANAL_RAPPELS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(libelleMoment(moment))
        .setGroup(cleGroupe(moment))
        .setGroupSummary(true)
        .setContentIntent(intentOuvertureApp())
        .build()

    private fun cleGroupe(moment: Moment) = "moment_${moment.name}"

    private fun intentOuvertureApp(): PendingIntent = PendingIntent.getActivity(
        contexte,
        0,
        Intent(contexte, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun intentAction(action: String, cle: CleRappel, dose: Double): PendingIntent {
        val intention = Intent(contexte, RecepteurActionPrise::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_DOSE, dose)
        }

        // Le code de requête distingue les deux actions d'une même clé.
        val code = cle.codeRequete() + if (action == ACTION_PRIS) 10_000_000 else 20_000_000

        return PendingIntent.getBroadcast(
            contexte,
            code,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun intentAlarme(cle: CleRappel, dose: Double): PendingIntent {
        val intention = Intent(contexte, AlarmeActivity::class.java).apply {
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_DOSE, dose)
        }

        return PendingIntent.getActivity(
            contexte,
            cle.codeRequete() + 30_000_000,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
