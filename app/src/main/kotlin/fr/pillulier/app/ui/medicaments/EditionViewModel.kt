package fr.pillulier.app.ui.medicaments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerMedicament
import fr.pillulier.app.usecase.SupprimerMedicament
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class EtatEdition(
    val id: Long = 0,
    val nom: String = "",
    val dosage: String = "",
    val forme: Forme = Forme.COMPRIME,
    val unitesParBoite: String = "30",
    val stockUnites: String = "0",
    val critique: Boolean = false,
    val type: TypeOrdonnance = TypeOrdonnance.PLANIFIEE,
    val rythme: Rythme = Rythme.TousLesJours,
    /** Texte brut du champ « un jour sur N » ; `rythme` ne peut pas porter une saisie en cours. */
    val intervalleJours: String = "2",
    val dateDebut: LocalDate = LocalDate.now(),
    val dateFin: LocalDate? = null,
    val doses: Map<Moment, Double> = emptyMap(),
    val seuilAlerteJours: String = "",
    val seuilAlerteUnites: String = "",
    val erreur: String? = null,
    val enregistre: Boolean = false,
)

@HiltViewModel
class EditionViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val enregistrerMedicament: EnregistrerMedicament,
    private val supprimerMedicament: SupprimerMedicament,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatEdition(dateDebut = horloge.aujourdhui()))
    val etat: StateFlow<EtatEdition> = _etat.asStateFlow()

    /**
     * Le `LaunchedEffect` de l'écran se relance à chaque recomposition initiale,
     * donc à chaque rotation : la vue survit, la composition non. Sans ce garde,
     * le second appel écraserait le formulaire en cours de saisie par la base.
     */
    private var dejaCharge = false

    fun charger(medicamentId: Long?) = viewModelScope.launch {
        if (medicamentId == null || medicamentId == 0L) return@launch
        if (dejaCharge) return@launch
        dejaCharge = true

        val medicament = medicaments.parId(medicamentId) ?: return@launch
        val ordonnance = ordonnances.enVigueur(medicamentId, horloge.aujourdhui())

        _etat.value = EtatEdition(
            id = medicament.id,
            nom = medicament.nom,
            dosage = medicament.dosage,
            forme = medicament.forme,
            unitesParBoite = medicament.unitesParBoite.toString(),
            stockUnites = medicament.stockUnites.toString(),
            critique = medicament.critique,
            type = ordonnance?.ordonnance?.type ?: TypeOrdonnance.PLANIFIEE,
            rythme = ordonnance?.ordonnance?.rythme ?: Rythme.TousLesJours,
            intervalleJours = (ordonnance?.ordonnance?.rythme as? Rythme.UnJourSurN)
                ?.n?.toString() ?: "2",
            dateDebut = ordonnance?.ordonnance?.dateDebut ?: horloge.aujourdhui(),
            dateFin = ordonnance?.ordonnance?.dateFin,
            doses = ordonnance?.doses?.associate { it.moment to it.dose } ?: emptyMap(),
            seuilAlerteJours = medicament.seuilAlerteJours?.toString() ?: "",
            seuilAlerteUnites = medicament.seuilAlerteUnites?.toString() ?: "",
        )
    }

    fun modifierNom(valeur: String) = _etat.update { it.copy(nom = valeur) }
    fun modifierDosage(valeur: String) = _etat.update { it.copy(dosage = valeur) }
    fun modifierForme(valeur: Forme) = _etat.update { it.copy(forme = valeur) }
    fun modifierUnitesParBoite(valeur: String) = _etat.update { it.copy(unitesParBoite = valeur) }
    fun modifierStock(valeur: String) = _etat.update { it.copy(stockUnites = valeur) }
    fun modifierCritique(valeur: Boolean) = _etat.update { it.copy(critique = valeur) }
    fun modifierType(valeur: TypeOrdonnance) = _etat.update { it.copy(type = valeur) }
    fun modifierDateDebut(valeur: LocalDate) = _etat.update { it.copy(dateDebut = valeur) }
    fun modifierDateFin(valeur: LocalDate?) = _etat.update { it.copy(dateFin = valeur) }
    fun modifierSeuilJours(valeur: String) = _etat.update { it.copy(seuilAlerteJours = valeur) }
    fun modifierSeuilUnites(valeur: String) = _etat.update { it.copy(seuilAlerteUnites = valeur) }

    fun choisirTousLesJours() = _etat.update { it.copy(rythme = Rythme.TousLesJours) }

    fun basculerJourDeSemaine(jour: DayOfWeek) = _etat.update { etat ->
        val actuels = (etat.rythme as? Rythme.JoursDeSemaine)?.jours ?: emptySet()
        val nouveaux = if (jour in actuels) actuels - jour else actuels + jour
        etat.copy(
            rythme = if (nouveaux.isEmpty()) Rythme.TousLesJours else Rythme.JoursDeSemaine(nouveaux),
        )
    }

    fun choisirUnJourSurN() = _etat.update {
        it.copy(rythme = Rythme.UnJourSurN(it.intervalleJours.toIntOrNull()?.takeIf(::intervalleValide) ?: 2))
    }

    /**
     * Le champ garde la saisie telle quelle ; le rythme ne suit que si elle se
     * lit, parce que `UnJourSurN` refuse un N inférieur à deux.
     */
    fun modifierIntervalle(valeur: String) = _etat.update { etat ->
        val n = valeur.toIntOrNull()
        etat.copy(
            intervalleJours = valeur,
            rythme = if (n != null && intervalleValide(n)) Rythme.UnJourSurN(n) else etat.rythme,
        )
    }

    /** Une dose nulle retire le moment de l'ordonnance. */
    fun definirDose(moment: Moment, dose: Double) = _etat.update { etat ->
        val doses = etat.doses.toMutableMap()
        if (dose <= 0.0) doses.remove(moment) else doses[moment] = dose
        etat.copy(doses = doses)
    }

    fun enregistrer() = viewModelScope.launch {
        val etat = _etat.value

        val unitesParBoite = etat.unitesParBoite.toIntOrNull()
        val stockUnites = etat.stockUnites.replace(',', '.').toDoubleOrNull()

        // Un champ numérique non vide qui ne se lit pas est une erreur de saisie,
        // pas un zéro : le domaine ne reçoit qu'un nombre et ne pourrait plus
        // faire la différence.
        if (etat.unitesParBoite.isNotBlank() && unitesParBoite == null) {
            _etat.update { it.copy(erreur = "Unités par boîte : nombre illisible") }
            return@launch
        }
        if (etat.stockUnites.isNotBlank() && stockUnites == null) {
            _etat.update { it.copy(erreur = "Stock : nombre illisible") }
            return@launch
        }
        if (etat.rythme is Rythme.UnJourSurN &&
            etat.intervalleJours.toIntOrNull()?.let(::intervalleValide) != true
        ) {
            _etat.update { it.copy(erreur = "Un jour sur : au moins 2 jours") }
            return@launch
        }

        try {
            enregistrerMedicament(
                medicament = Medicament(
                    id = etat.id,
                    nom = etat.nom.trim(),
                    dosage = etat.dosage.trim(),
                    forme = etat.forme,
                    unitesParBoite = unitesParBoite ?: 0,
                    stockUnites = stockUnites ?: 0.0,
                    seuilAlerteJours = etat.seuilAlerteJours.toIntOrNull(),
                    seuilAlerteUnites = etat.seuilAlerteUnites.toIntOrNull(),
                    critique = etat.critique,
                ),
                type = etat.type,
                rythme = etat.rythme,
                dateDebut = etat.dateDebut,
                dateFin = etat.dateFin,
                doses = etat.doses.map { (moment, dose) -> DosePrescrite(moment, dose) },
                // Pas encore de selecteur de date d'effet (Task 5) : la modification
                // s'applique des aujourd'hui, ou a partir du debut si le traitement
                // n'a pas encore commence.
                dateEffet = maxOf(horloge.aujourdhui(), etat.dateDebut),
            )
            _etat.update { it.copy(erreur = null, enregistre = true) }
        } catch (erreur: IllegalArgumentException) {
            _etat.update { it.copy(erreur = erreur.message) }
        }
    }

    fun supprimer() = viewModelScope.launch {
        val id = _etat.value.id
        if (id != 0L) supprimerMedicament(id)
        _etat.update { it.copy(enregistre = true) }
    }
}

private fun intervalleValide(n: Int): Boolean = n >= 2
