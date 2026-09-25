package fr.pillulier.app.data

import fr.pillulier.app.data.db.DosePrescriteEntity
import fr.pillulier.app.data.db.EvenementPriseEntity
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.OrdonnanceAvecDosesEntity
import fr.pillulier.app.data.db.OrdonnanceEntity
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.EvenementPrise
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import fr.pillulier.domain.Rythme
import java.time.DayOfWeek

private const val TOUS_LES_JOURS = "TOUS_LES_JOURS"
private const val JOURS_DE_SEMAINE = "JOURS_DE_SEMAINE"
private const val UN_JOUR_SUR_N = "UN_JOUR_SUR_N"

/** Aplatit un rythme en ses trois colonnes : discriminant, jours, N. */
fun Rythme.versColonnes(): Triple<String, String?, Int?> = when (this) {
    Rythme.TousLesJours -> Triple(TOUS_LES_JOURS, null, null)
    is Rythme.JoursDeSemaine -> Triple(JOURS_DE_SEMAINE, jours.joinToString(",") { it.name }, null)
    is Rythme.UnJourSurN -> Triple(UN_JOUR_SUR_N, null, n)
}

fun rythmeDepuisColonnes(type: String, jours: String?, n: Int?): Rythme = when (type) {
    TOUS_LES_JOURS -> Rythme.TousLesJours
    JOURS_DE_SEMAINE -> Rythme.JoursDeSemaine(
        requireNotNull(jours) { "rythme $JOURS_DE_SEMAINE sans jours" }
            .split(",")
            .filter { it.isNotBlank() }
            .map(DayOfWeek::valueOf)
            .toSet(),
    )
    UN_JOUR_SUR_N -> Rythme.UnJourSurN(requireNotNull(n) { "rythme $UN_JOUR_SUR_N sans N" })
    else -> error("rythme inconnu : $type")
}

fun MedicamentEntity.versDomaine() = Medicament(
    id = id,
    nom = nom,
    dosage = dosage,
    forme = forme,
    unitesParBoite = unitesParBoite,
    stockUnites = stockUnites,
    seuilAlerteJours = seuilAlerteJours,
    seuilAlerteUnites = seuilAlerteUnites,
    critique = critique,
    archiveLe = archiveLe,
)

fun Medicament.versEntite() = MedicamentEntity(
    id = id,
    nom = nom,
    dosage = dosage,
    forme = forme,
    unitesParBoite = unitesParBoite,
    stockUnites = stockUnites,
    seuilAlerteJours = seuilAlerteJours,
    seuilAlerteUnites = seuilAlerteUnites,
    critique = critique,
    archiveLe = archiveLe,
)

fun OrdonnanceAvecDosesEntity.versDomaine() = OrdonnanceAvecDoses(
    ordonnance = Ordonnance(
        id = ordonnance.id,
        medicamentId = ordonnance.medicamentId,
        type = ordonnance.type,
        rythme = rythmeDepuisColonnes(ordonnance.rythmeType, ordonnance.rythmeJours, ordonnance.rythmeN),
        dateDebut = ordonnance.dateDebut,
        dateFin = ordonnance.dateFin,
        dateAncrage = ordonnance.dateAncrage,
    ),
    doses = doses.map { DosePrescrite(it.moment, it.dose) },
)

fun Ordonnance.versEntite(): OrdonnanceEntity {
    val (type, jours, n) = rythme.versColonnes()
    return OrdonnanceEntity(
        id = id,
        medicamentId = medicamentId,
        type = this.type,
        rythmeType = type,
        rythmeJours = jours,
        rythmeN = n,
        dateDebut = dateDebut,
        dateFin = dateFin,
        dateAncrage = dateAncrage,
    )
}

fun DosePrescrite.versEntite(ordonnanceId: Long) = DosePrescriteEntity(
    ordonnanceId = ordonnanceId,
    moment = moment,
    dose = dose,
)

fun EvenementPriseEntity.versDomaine() = EvenementPrise(
    id = id,
    medicamentId = medicamentId,
    date = date,
    moment = moment,
    doseReelle = doseReelle,
    enregistreLe = enregistreLe,
)
