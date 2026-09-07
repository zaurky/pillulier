package fr.pillulier.app.rappels

import fr.pillulier.domain.CleRappel
import java.time.LocalDateTime

/**
 * Frontière derrière laquelle vit `AlarmManager`. Le réarmement se teste ainsi
 * sans Android, et rien n'est stocké : la clé suffit à annuler ou reprogrammer.
 */
interface ProgrammateurAlarmes {
    fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean)
    fun annuler(cle: CleRappel)
}
