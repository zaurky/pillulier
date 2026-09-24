package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écrit le médicament et son ordonnance, puis réarme la fenêtre : toute
 * modification de posologie doit se refléter tout de suite dans les alarmes.
 */
class EnregistrerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(
        medicament: Medicament,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
    ): Long {
        require(medicament.nom.isNotBlank()) { "le nom du medicament est obligatoire" }
        require(medicament.unitesParBoite > 0) { "une boite contient au moins une unite" }
        require(dateFin == null || !dateFin.isBefore(dateDebut)) {
            "la date de fin ne peut pas preceder la date de debut"
        }
        if (type == TypeOrdonnance.PLANIFIEE) {
            require(doses.isNotEmpty()) { "une ordonnance planifiee doit porter au moins une dose" }
            require(doses.all { it.dose > 0.0 }) { "une dose doit etre strictement positive" }
            require(doses.map { it.moment }.distinct().size == doses.size) {
                "un moment ne peut porter qu une dose"
            }
        }

        val id = medicaments.enregistrer(medicament)

        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = type,
                rythme = rythme,
                dateDebut = dateDebut,
                dateFin = dateFin,
                dateAncrage = dateDebut,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
        )

        reArmerRappels()
        rafraichirWidget()
        return id
    }
}

/**
 * Le réarmement annule un surensemble bâti sur `medicaments.tous()` : une fois
 * la ligne effacée, l'identifiant en a disparu et sa fenêtre d'alarmes resterait
 * armée, sa notification devenant inatteignable — indéboulonnable si elle est
 * critique. On ferme donc sa fenêtre **avant** de supprimer.
 */
class SupprimerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val reArmerRappels: ReArmerRappels,
    private val horloge: Horloge,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(medicamentId: Long) {
        val aujourdhui = horloge.aujourdhui()
        (-1L until ReArmerRappels.JOURS_FENETRE).forEach { decalage ->
            val jour = aujourdhui.plusDays(decalage)
            Moment.entries.forEach { moment ->
                val cle = CleRappel(medicamentId, jour, moment)
                programmateur.annuler(cle)
                notifications.retirer(cle)
            }
        }

        medicaments.supprimer(medicamentId)
        reArmerRappels()
        rafraichirWidget()
    }
}
