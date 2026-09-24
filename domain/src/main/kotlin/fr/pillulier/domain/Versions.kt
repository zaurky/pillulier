package fr.pillulier.domain

import java.time.LocalDate

/**
 * La version d'ordonnance d'un medicament active a cette date.
 *
 * Quand aucune ne couvre la date, rend la plus recente qui precede : la fiche
 * d'un medicament archive doit montrer sa derniere prescription connue plutot
 * qu'un formulaire vide. A defaut, la premiere a venir, pour qu'une ordonnance
 * qui demarre demain soit deja lisible. Nul seulement si le medicament n'a
 * aucune ordonnance.
 */
fun List<OrdonnanceAvecDoses>.enVigueur(
    medicamentId: Long,
    date: LocalDate,
): OrdonnanceAvecDoses? {
    val siennes = filter { it.ordonnance.medicamentId == medicamentId }

    return siennes.firstOrNull { couvre(it.ordonnance, date) }
        ?: siennes.filter { it.ordonnance.dateDebut <= date }.maxByOrNull { it.ordonnance.dateDebut }
        ?: siennes.minByOrNull { it.ordonnance.dateDebut }
}

private fun couvre(ordonnance: Ordonnance, date: LocalDate): Boolean =
    date >= ordonnance.dateDebut && (ordonnance.dateFin?.let { date <= it } ?: true)
