package fr.pillulier.domain

import java.time.LocalDateTime

/**
 * Statut dérivé, jamais persisté. Une prise non cochée dont la journée est
 * terminée est oubliée par la seule absence d'événement.
 */
fun statut(
    prise: PriseAttendue,
    evenements: List<EvenementPrise>,
    maintenant: LocalDateTime,
): StatutPrise {
    val jour = prise.heure.toLocalDate()

    val faite = evenements.any {
        it.medicamentId == prise.medicamentId && it.moment == prise.moment && it.date == jour
    }
    if (faite) return StatutPrise.PRISE

    return when {
        jour < maintenant.toLocalDate() -> StatutPrise.OUBLIEE
        !prise.heure.isAfter(maintenant) -> StatutPrise.EN_RETARD
        else -> StatutPrise.A_VENIR
    }
}
