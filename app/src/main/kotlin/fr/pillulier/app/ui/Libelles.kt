package fr.pillulier.app.ui

import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.DayOfWeek

/** `0,5` s'écrit `½`, `1,5` s'écrit `1½`, une valeur entière sans décimale. */
fun formaterDose(dose: Double): String {
    val entiere = dose.toInt()
    val reste = dose - entiere

    return when {
        reste == 0.0 -> entiere.toString()
        reste == 0.5 -> if (entiere == 0) "½" else "$entiere½"
        else -> dose.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }
}

fun libelleForme(forme: Forme, dose: Double): String {
    val singulier = when (forme) {
        Forme.COMPRIME -> "comprimé"
        Forme.GELULE -> "gélule"
        Forme.SACHET -> "sachet"
        Forme.INJECTION -> "injection"
    }
    return if (dose > 1.0) singulier + "s" else singulier
}

fun libelleDoseComplete(dose: Double, forme: Forme): String =
    "${formaterDose(dose)} ${libelleForme(forme, dose)}"

fun libelleMoment(moment: Moment): String = when (moment) {
    Moment.MATIN -> "Matin"
    Moment.MIDI -> "Midi"
    Moment.SOIR -> "Soir"
    Moment.COUCHER -> "Coucher"
}

fun libelleStatut(statut: StatutPrise): String = when (statut) {
    StatutPrise.A_VENIR -> "À venir"
    StatutPrise.EN_RETARD -> "En retard"
    StatutPrise.PRISE -> "Prise"
    StatutPrise.OUBLIEE -> "Oubliée"
}

/** Abréviation à deux lettres, dans l'ordre où les puces de l'écran d'édition les affichent. */
fun libelleJour(jour: DayOfWeek): String = when (jour) {
    DayOfWeek.MONDAY -> "Lu"
    DayOfWeek.TUESDAY -> "Ma"
    DayOfWeek.WEDNESDAY -> "Me"
    DayOfWeek.THURSDAY -> "Je"
    DayOfWeek.FRIDAY -> "Ve"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "Di"
}
