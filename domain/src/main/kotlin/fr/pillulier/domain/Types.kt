package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Formes dénombrables gérées par l'application. */
enum class Forme { COMPRIME, GELULE, SACHET, INJECTION }

/** Les quatre moments globaux, dans l'ordre chronologique de la journée. */
enum class Moment { MATIN, MIDI, SOIR, COUCHER }

enum class TypeOrdonnance { PLANIFIEE, A_LA_DEMANDE }

enum class StatutPrise { A_VENIR, EN_RETARD, PRISE, OUBLIEE }

/** Rythme d'une ordonnance planifiée. */
sealed interface Rythme {
    data object TousLesJours : Rythme

    data class JoursDeSemaine(val jours: Set<DayOfWeek>) : Rythme

    /** Un jour sur [n], compté depuis la date d'ancrage de l'ordonnance. */
    data class UnJourSurN(val n: Int) : Rythme {
        init {
            require(n >= 2) { "un jour sur N exige n >= 2, reçu $n" }
        }
    }
}

data class Medicament(
    val id: Long,
    val nom: String,
    val dosage: String,
    val forme: Forme,
    val unitesParBoite: Int,
    val stockUnites: Double,
    /** Seuil d'alerte en jours pour une ordonnance planifiée ; nul = valeur par défaut des préférences. */
    val seuilAlerteJours: Int?,
    /** Seuil d'alerte en unités pour une ordonnance à la demande. */
    val seuilAlerteUnites: Int?,
    /** Déclenche l'alarme plein écran au lieu d'une notification classique. */
    val critique: Boolean,
    /** Non nulle = retiré de la liste et du planning, mais son historique demeure. */
    val archiveLe: Instant? = null,
)

data class DosePrescrite(val moment: Moment, val dose: Double)

data class Ordonnance(
    val id: Long,
    val medicamentId: Long,
    val type: TypeOrdonnance,
    val rythme: Rythme,
    val dateDebut: LocalDate,
    /** Nulle = traitement au long cours. */
    val dateFin: LocalDate?,
    /**
     * Origine du rythme, distincte de [dateDebut] qui borne cette version.
     * Les versions successives d'une meme prescription la partagent, sans quoi
     * scinder une ordonnance « un jour sur N » decalerait sa phase.
     */
    val dateAncrage: LocalDate,
)

data class OrdonnanceAvecDoses(
    val ordonnance: Ordonnance,
    val doses: List<DosePrescrite>,
)

/** Journal du réel : une instance n'existe que si la prise a eu lieu. */
data class EvenementPrise(
    val id: Long,
    val medicamentId: Long,
    val date: LocalDate,
    /** Nul pour une prise à la demande, hors planning. */
    val moment: Moment?,
    val doseReelle: Double,
    val enregistreLe: Instant,
)

/** Prise calculée par le moteur, jamais persistée. */
data class PriseAttendue(
    val medicamentId: Long,
    val moment: Moment,
    val dose: Double,
    val heure: LocalDateTime,
)
