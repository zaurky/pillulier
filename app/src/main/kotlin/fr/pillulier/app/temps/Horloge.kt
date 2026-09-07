package fr.pillulier.app.temps

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Injectée partout où l'heure courante intervient, pour rendre le code testable. */
interface Horloge {
    fun maintenant(): LocalDateTime
    fun aujourdhui(): LocalDate
    fun instant(): Instant
}

class HorlogeSysteme @Inject constructor() : Horloge {
    override fun maintenant(): LocalDateTime = LocalDateTime.now()
    override fun aujourdhui(): LocalDate = LocalDate.now()
    override fun instant(): Instant = Instant.now()
}
