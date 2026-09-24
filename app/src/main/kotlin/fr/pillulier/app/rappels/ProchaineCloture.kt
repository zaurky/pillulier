package fr.pillulier.app.rappels

import java.time.LocalDateTime
import java.time.LocalTime

/** Minuit passé de cinq minutes : la marge évite de courir la bascule de date. */
val HEURE_CLOTURE: LocalTime = LocalTime.of(0, 5)

/**
 * Prochaine occurrence de l'heure de clôture, strictement future. À l'heure
 * pile on vise le lendemain : l'alarme qui vient de se déclencher se
 * reprogramme depuis son propre récepteur, et rendre le jour même la ferait
 * boucler sur l'instant présent.
 */
fun prochaineCloture(maintenant: LocalDateTime): LocalDateTime =
    maintenant.toLocalDate()
        .let { if (maintenant.toLocalTime() >= HEURE_CLOTURE) it.plusDays(1) else it }
        .atTime(HEURE_CLOTURE)
