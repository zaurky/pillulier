package fr.pillulier.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Vrai si l'ordonnance planifiée prévoit une prise à cette date : elle est dans
 * la fenêtre début/fin, et son rythme retient le jour.
 */
fun estJourActif(ordonnance: Ordonnance, date: LocalDate): Boolean {
    if (ordonnance.type != TypeOrdonnance.PLANIFIEE) return false
    if (date < ordonnance.dateDebut) return false
    ordonnance.dateFin?.let { if (date > it) return false }

    return when (val rythme = ordonnance.rythme) {
        Rythme.TousLesJours -> true
        is Rythme.JoursDeSemaine -> date.dayOfWeek in rythme.jours
        is Rythme.UnJourSurN ->
            (date.toEpochDay() - ordonnance.dateDebut.toEpochDay()) % rythme.n == 0L
    }
}

/**
 * Unique chemin de calcul du planning : Aujourd'hui, la vue semaine, la
 * programmation des alarmes et le widget d'écran d'accueil passent tous les
 * quatre par ici.
 */
fun prisesAttendues(
    date: LocalDate,
    ordonnances: List<OrdonnanceAvecDoses>,
    heures: Map<Moment, LocalTime>,
): List<PriseAttendue> {
    require(heures.keys.containsAll(Moment.entries.toSet())) {
        "les quatre moments doivent avoir une heure, reçu ${heures.keys}"
    }

    return ordonnances
        .filter { estJourActif(it.ordonnance, date) }
        .flatMap { avecDoses ->
            avecDoses.doses.map { dose ->
                PriseAttendue(
                    medicamentId = avecDoses.ordonnance.medicamentId,
                    moment = dose.moment,
                    dose = dose.dose,
                    heure = LocalDateTime.of(date, heures.getValue(dose.moment)),
                )
            }
        }
        .sortedWith(compareBy({ it.heure }, { it.medicamentId }))
}
