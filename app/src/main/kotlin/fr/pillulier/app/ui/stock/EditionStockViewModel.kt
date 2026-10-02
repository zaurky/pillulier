package fr.pillulier.app.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerFicheMedicament
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EtatEditionStock(
    val id: Long = 0,
    val nom: String = "",
    val dosage: String = "",
    val forme: Forme = Forme.COMPRIME,
    val unitesParBoite: String = "",
    val stockUnites: String = "",
    val seuilAlerteJours: String = "",
    val seuilAlerteUnites: String = "",
    /** Lu sur l'ordonnance en vigueur, jamais édité ici : décide quel seuil s'affiche. */
    val aLaDemande: Boolean = false,
    /**
     * Ni l'un ni l'autre ne s'éditent sur cet écran, mais `@Update` réécrit la
     * ligne entière : les omettre désarchiverait le médicament et éteindrait
     * son alarme critique à chaque correction de stock.
     */
    val critique: Boolean = false,
    val archiveLe: Instant? = null,
    val erreur: String? = null,
    val enregistre: Boolean = false,
)

@HiltViewModel
class EditionStockViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val enregistrerFicheMedicament: EnregistrerFicheMedicament,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatEditionStock())
    val etat: StateFlow<EtatEditionStock> = _etat.asStateFlow()

    /** Meme garde que EditionViewModel : la rotation relance le `LaunchedEffect`. */
    private var dejaCharge = false

    fun charger(medicamentId: Long) = viewModelScope.launch {
        if (dejaCharge) return@launch
        dejaCharge = true

        val medicament = medicaments.parId(medicamentId) ?: return@launch
        val ordonnance = ordonnances.enVigueur(medicamentId, horloge.aujourdhui())

        _etat.value = EtatEditionStock(
            id = medicament.id,
            nom = medicament.nom,
            dosage = medicament.dosage,
            forme = medicament.forme,
            unitesParBoite = medicament.unitesParBoite.toString(),
            stockUnites = medicament.stockUnites.toString(),
            seuilAlerteJours = medicament.seuilAlerteJours?.toString() ?: "",
            seuilAlerteUnites = medicament.seuilAlerteUnites?.toString() ?: "",
            aLaDemande = ordonnance?.ordonnance?.type == TypeOrdonnance.A_LA_DEMANDE,
            critique = medicament.critique,
            archiveLe = medicament.archiveLe,
        )
    }

    fun modifierNom(valeur: String) = _etat.update { it.copy(nom = valeur) }
    fun modifierDosage(valeur: String) = _etat.update { it.copy(dosage = valeur) }
    fun modifierForme(valeur: Forme) = _etat.update { it.copy(forme = valeur) }
    fun modifierUnitesParBoite(valeur: String) = _etat.update { it.copy(unitesParBoite = valeur) }
    fun modifierStock(valeur: String) = _etat.update { it.copy(stockUnites = valeur) }
    fun modifierSeuilJours(valeur: String) = _etat.update { it.copy(seuilAlerteJours = valeur) }
    fun modifierSeuilUnites(valeur: String) = _etat.update { it.copy(seuilAlerteUnites = valeur) }

    fun enregistrer() = viewModelScope.launch {
        val etat = _etat.value

        // Cet ecran ne cree jamais : une fiche ecrite sans medicament charge
        // naitrait sans prescription, ce que la creation atomique interdit.
        if (etat.id == 0L) return@launch

        val unitesParBoite = etat.unitesParBoite.toIntOrNull()
        val stockUnites = etat.stockUnites.replace(',', '.').toDoubleOrNull()
        val seuilJours = etat.seuilAlerteJours.toIntOrNull()
        val seuilUnites = etat.seuilAlerteUnites.toIntOrNull()

        // Un champ non vide qui ne se lit pas est une erreur de saisie, pas un
        // zero ni un « defaut » : pour un seuil, la confusion effacerait en
        // silence un reglage sur une faute de frappe.
        val illisible = when {
            etat.unitesParBoite.isNotBlank() && unitesParBoite == null -> "Unités par boîte : nombre illisible"
            etat.stockUnites.isNotBlank() && stockUnites == null -> "Stock : nombre illisible"
            etat.seuilAlerteJours.isNotBlank() && seuilJours == null -> "Seuil d'alerte : nombre illisible"
            etat.seuilAlerteUnites.isNotBlank() && seuilUnites == null -> "Seuil d'alerte : nombre illisible"
            else -> null
        }
        if (illisible != null) {
            _etat.update { it.copy(erreur = illisible) }
            return@launch
        }

        try {
            enregistrerFicheMedicament(
                Medicament(
                    id = etat.id,
                    nom = etat.nom.trim(),
                    dosage = etat.dosage.trim(),
                    forme = etat.forme,
                    unitesParBoite = unitesParBoite ?: 0,
                    stockUnites = stockUnites ?: 0.0,
                    seuilAlerteJours = seuilJours,
                    seuilAlerteUnites = seuilUnites,
                    critique = etat.critique,
                    archiveLe = etat.archiveLe,
                ),
            )
            _etat.update { it.copy(erreur = null, enregistre = true) }
        } catch (erreur: IllegalArgumentException) {
            _etat.update { it.copy(erreur = erreur.message) }
        }
    }
}
