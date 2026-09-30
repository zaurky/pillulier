package fr.pillulier.app.temps

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Injectée partout où l'heure courante intervient, pour rendre le code testable. */
interface Horloge {
    fun maintenant(): LocalDateTime
    fun aujourdhui(): LocalDate
    fun instant(): Instant

    /**
     * La date du jour, réémise à chaque passage de minuit. Un écran qui se
     * contente d'appeler [aujourdhui] une fois grave la date de sa construction :
     * l'app laissée ouverte la nuit montre encore la veille au matin.
     */
    fun jours(): Flow<LocalDate>
}

class HorlogeSysteme @Inject constructor() : Horloge {
    override fun maintenant(): LocalDateTime = LocalDateTime.now()
    override fun aujourdhui(): LocalDate = LocalDate.now()
    override fun instant(): Instant = Instant.now()

    override fun jours(): Flow<LocalDate> = flow {
        while (true) {
            val jour = aujourdhui()
            emit(jour)
            // Une seconde après minuit, jamais juste avant : se réveiller trop
            // tôt réémettrait la même date en rafale. Le réveil peut arriver en
            // retard si l'appareil dort — sans importance, car toute
            // réabonnement relit la date en tête de boucle.
            val reveil = jour.plusDays(1).atStartOfDay().plusSeconds(1)
            delay(Duration.between(maintenant(), reveil).toMillis().coerceAtLeast(1_000))
        }
    }.distinctUntilChanged()
}
