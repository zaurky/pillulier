package fr.pillulier.domain

import java.time.LocalDate
import java.time.MonthDay

/**
 * Jours fériés de France métropolitaine, calculés sans aucune donnée stockée.
 * Ne couvre ni les deux jours d'Alsace-Moselle ni les dates d'outre-mer.
 */
object JoursFeriesFrance {

    private val joursFixes = setOf(
        MonthDay.of(1, 1),   // Jour de l'an
        MonthDay.of(5, 1),   // Fête du Travail
        MonthDay.of(5, 8),   // Victoire 1945
        MonthDay.of(7, 14),  // Fête nationale
        MonthDay.of(8, 15),  // Assomption
        MonthDay.of(11, 1),  // Toussaint
        MonthDay.of(11, 11), // Armistice 1918
        MonthDay.of(12, 25), // Noël
    )

    /** Dimanche de Pâques, par l'algorithme de Meeus pour le calendrier grégorien. */
    fun paques(annee: Int): LocalDate {
        val a = annee % 19
        val b = annee / 100
        val c = annee % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val n = h + l - 7 * m + 114
        return LocalDate.of(annee, n / 31, (n % 31) + 1)
    }

    /** Les trois fériés mobiles : lundi de Pâques, Ascension, lundi de Pentecôte. */
    private fun joursMobiles(annee: Int): Set<LocalDate> {
        val paques = paques(annee)
        return setOf(
            paques.plusDays(1),
            paques.plusDays(39),
            paques.plusDays(50),
        )
    }

    fun estFerie(date: LocalDate): Boolean =
        MonthDay.from(date) in joursFixes || date in joursMobiles(date.year)
}
