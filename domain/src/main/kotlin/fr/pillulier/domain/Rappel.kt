package fr.pillulier.domain

import java.time.LocalDate

/** Identifie un rappel : un médicament, un jour, un moment. */
data class CleRappel(
    val medicamentId: Long,
    val date: LocalDate,
    val moment: Moment,
)

/**
 * Identifiant stable et positif, calculé sans état : il sert de `requestCode` de
 * `PendingIntent` et d'identifiant de notification, ce qui permet de reconstruire
 * ou d'annuler un rappel sans avoir mémorisé quoi que ce soit.
 *
 * Injectif tant que les identifiants de médicament restent sous 1000 et que la
 * fenêtre de programmation reste sous 1000 jours — la fenêtre réelle est de trois
 * jours.
 */
fun CleRappel.codeRequete(): Int {
    val jour = (date.toEpochDay().mod(1_000L)).toInt()
    val medicament = (medicamentId.mod(1_000L)).toInt()
    return jour * 4_000 + moment.ordinal * 1_000 + medicament
}
