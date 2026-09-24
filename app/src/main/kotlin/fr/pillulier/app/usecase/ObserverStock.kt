package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.domain.JoursFeriesFrance
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.dateAlerte
import fr.pillulier.domain.enVigueur
import fr.pillulier.domain.projectionStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class LigneStock(
    val medicamentId: Long,
    val nom: String,
    val dosage: String,
    val unitesRestantes: Double,
    val libelleUnites: String,
    val joursRestants: Long?,
    val dateEpuisement: LocalDate?,
    val dateAlerte: LocalDate?,
    val aLaDemande: Boolean,
    val enAlerte: Boolean,
)

class ObserverStock @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val preferences: DepotPreferences,
    private val horloge: Horloge,
) {
    operator fun invoke(): Flow<List<LigneStock>> = combine(
        medicaments.observerTous(),
        ordonnances.observerToutes(),
        preferences.preferences,
    ) { tousMedicaments, toutesOrdonnances, prefs ->
        val aujourdhui = horloge.aujourdhui()

        tousMedicaments.map { medicament ->
            val ordonnance = toutesOrdonnances.enVigueur(medicament.id, aujourdhui)
            val aLaDemande = ordonnance == null ||
                ordonnance.ordonnance.type == TypeOrdonnance.A_LA_DEMANDE

            val epuisement = if (aLaDemande) {
                null
            } else {
                projectionStock(medicament.stockUnites, ordonnance!!, aujourdhui)
            }

            val alerte = epuisement?.let {
                dateAlerte(
                    dateEpuisement = it,
                    seuilJours = medicament.seuilAlerteJours ?: prefs.seuilAlerteJoursDefaut,
                    estFerie = JoursFeriesFrance::estFerie,
                )
            }

            LigneStock(
                medicamentId = medicament.id,
                nom = medicament.nom,
                dosage = medicament.dosage,
                unitesRestantes = medicament.stockUnites,
                libelleUnites = libelleDoseComplete(medicament.stockUnites, medicament.forme),
                joursRestants = epuisement?.let { ChronoUnit.DAYS.between(aujourdhui, it) },
                dateEpuisement = epuisement,
                dateAlerte = alerte,
                aLaDemande = aLaDemande,
                enAlerte = if (aLaDemande) {
                    medicament.seuilAlerteUnites?.let { medicament.stockUnites <= it } ?: false
                } else {
                    alerte != null && !aujourdhui.isBefore(alerte)
                },
            )
        }
    }
}
