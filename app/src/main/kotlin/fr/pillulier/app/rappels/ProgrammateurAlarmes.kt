package fr.pillulier.app.rappels

import fr.pillulier.domain.CleRappel
import java.time.LocalDateTime

/**
 * Frontière derrière laquelle vit `AlarmManager`. Le réarmement se teste ainsi
 * sans Android, et rien n'est stocké : la clé suffit à annuler ou reprogrammer.
 */
interface ProgrammateurAlarmes {
    fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean, phase: Phase = Phase.SONNERIE)
    fun annuler(cle: CleRappel)
}

/**
 * Une prise s'annonce en silence avant son heure, pour être cochée d'avance,
 * puis sonne à l'heure. Les deux phases partagent la clé, donc une seule
 * alarme : le préavis programme lui-même la sonnerie.
 */
enum class Phase { PREAVIS, SONNERIE }

const val PREAVIS_MINUTES = 30L

/**
 * Premier déclenchement d'une prise prévue à [heure]. Dans la fenêtre du
 * préavis, il part tout de suite plutôt que d'être perdu ; une prise déjà
 * due sonne tout de suite.
 */
fun premierDeclenchement(heure: LocalDateTime, maintenant: LocalDateTime): Pair<LocalDateTime, Phase> {
    val preavis = heure.minusMinutes(PREAVIS_MINUTES)
    return when {
        preavis.isAfter(maintenant) -> preavis to Phase.PREAVIS
        heure.isAfter(maintenant) -> maintenant.plusMinutes(1) to Phase.PREAVIS
        else -> maintenant.plusMinutes(1) to Phase.SONNERIE
    }
}
