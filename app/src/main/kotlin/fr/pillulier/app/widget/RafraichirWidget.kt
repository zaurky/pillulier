package fr.pillulier.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import javax.inject.Inject

/**
 * Une session Glance est bornée dans le temps (`TimeoutOptions` : 45 s à
 * l'ouverture, 5 s de plus par événement, 5 s en Doze) : dans le cas courant,
 * aucune session ne tourne quand une prise est enregistrée, donc le flux
 * collecté ne fait rien tant que rien ne demande explicitement un `updateAll`.
 *
 * La règle est simple : qui réarme les rappels rafraîchit le widget. C'est
 * appelé après toute écriture qui change ce que la journée affiche, ou ce que
 * `ReArmerRappels` recalcule — la coche et l'enregistrement à la demande
 * depuis l'écran Aujourd'hui (`AujourdhuiViewModel`), les actions *Pris* et
 * *Plus tard* d'une notification (`RecepteurActionPrise`), un changement
 * d'heure de moment (`PreferencesViewModel`), l'enregistrement ou la
 * suppression d'un médicament (`EnregistrerMedicament`, `SupprimerMedicament`),
 * le passage à un nouveau jour (`ClotureQuotidienne`, réveillée par l'alarme
 * exacte de minuit) et le rattrapage après
 * démarrage ou mise à jour (`RecepteurDemarrage`). `updateAll` est sans effet
 * quand aucun widget n'est posé : l'appeler ne coûte rien dans ce cas.
 */
class RafraichirWidget @Inject constructor(private val contexte: Context) {
    suspend operator fun invoke() = PillulierWidget.updateAll(contexte)
}
