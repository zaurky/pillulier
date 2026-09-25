package fr.pillulier.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.TypeOrdonnance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "medicament")
data class MedicamentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val dosage: String,
    val forme: Forme,
    val unitesParBoite: Int,
    val stockUnites: Double,
    val seuilAlerteJours: Int?,
    val seuilAlerteUnites: Int?,
    val critique: Boolean,
    /** Non nul = retire de la liste et du planning, mais son historique demeure. */
    val archiveLe: Instant? = null,
)

/** Un medicament porte N ordonnances, disjointes dans le temps. */
@Entity(
    tableName = "ordonnance",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicamentId", "dateDebut"])],
)
data class OrdonnanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val type: TypeOrdonnance,
    /** `TOUS_LES_JOURS`, `JOURS_DE_SEMAINE` ou `UN_JOUR_SUR_N`. */
    val rythmeType: String,
    /** Jours de semaine séparés par des virgules, ex. `MONDAY,THURSDAY`. */
    val rythmeJours: String?,
    val rythmeN: Int?,
    val dateDebut: LocalDate,
    val dateFin: LocalDate?,
    val dateAncrage: LocalDate,
)

@Entity(
    tableName = "dose_prescrite",
    foreignKeys = [
        ForeignKey(
            entity = OrdonnanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["ordonnanceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ordonnanceId", "moment"], unique = true)],
)
data class DosePrescriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ordonnanceId: Long,
    val moment: Moment,
    val dose: Double,
)

@Entity(tableName = "moment_config")
data class MomentConfigEntity(
    @PrimaryKey val moment: Moment,
    val heure: LocalTime,
)

/**
 * Journal du réel. L'index unique rend l'enregistrement d'une prise planifiée
 * idempotent ; comme SQLite considère deux `NULL` comme distincts, les prises à
 * la demande (`moment` nul) ne sont pas contraintes.
 */
@Entity(
    tableName = "evenement_prise",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["medicamentId", "date", "moment"], unique = true),
        Index(value = ["date"]),
    ],
)
data class EvenementPriseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val date: LocalDate,
    val moment: Moment?,
    val doseReelle: Double,
    val enregistreLe: Instant,
)

data class OrdonnanceAvecDosesEntity(
    @Embedded val ordonnance: OrdonnanceEntity,
    @Relation(parentColumn = "id", entityColumn = "ordonnanceId")
    val doses: List<DosePrescriteEntity>,
)
