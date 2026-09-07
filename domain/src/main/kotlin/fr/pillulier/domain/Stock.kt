package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** Tolérance de comparaison des doses décimales. */
private const val EPSILON = 1e-9

/** Somme des doses prévues ce jour-là, ou zéro si le rythme ne retient pas le jour. */
fun consommationDuJour(ordonnance: OrdonnanceAvecDoses, date: LocalDate): Double =
    if (estJourActif(ordonnance.ordonnance, date)) ordonnance.doses.sumOf { it.dose } else 0.0

/**
 * Premier jour dont la consommation prévue ne peut plus être servie, ou `null`
 * s'il n'y a pas de rupture à annoncer : l'ordonnance s'arrête avant, ou le stock
 * survit à l'horizon.
 *
 * L'itération jour par jour est nécessaire : un rythme irrégulier ou une cure
 * datée donnent une consommation qu'une division ne capture pas.
 */
fun projectionStock(
    stockUnites: Double,
    ordonnance: OrdonnanceAvecDoses,
    depuis: LocalDate,
    horizonJours: Int = 365,
): LocalDate? {
    var restant = stockUnites
    var jour = depuis

    repeat(horizonJours) {
        ordonnance.ordonnance.dateFin?.let { fin -> if (jour > fin) return null }

        val conso = consommationDuJour(ordonnance, jour)
        if (conso > EPSILON) {
            if (restant < conso - EPSILON) return jour
            restant -= conso
        }
        jour = jour.plusDays(1)
    }

    return null
}

/**
 * Date à laquelle prévenir qu'il faut renouveler : le seuil en jours retiré de la
 * date d'épuisement, reculé d'un jour supplémentaire si un dimanche ou un jour
 * férié tombe dans la fenêtre — la pharmacie est alors fermée. Un seul jour de
 * marge est ajouté, quel que soit le nombre de jours fermés.
 */
fun dateAlerte(
    dateEpuisement: LocalDate,
    seuilJours: Int,
    estFerie: (LocalDate) -> Boolean,
): LocalDate {
    val candidat = dateEpuisement.minusDays(seuilJours.toLong())

    val fenetreContientJourFerme = generateSequence(candidat) { it.plusDays(1) }
        .takeWhile { !it.isAfter(dateEpuisement) }
        .any { it.dayOfWeek == DayOfWeek.SUNDAY || estFerie(it) }

    return if (fenetreContientJourFerme) candidat.minusDays(1) else candidat
}
