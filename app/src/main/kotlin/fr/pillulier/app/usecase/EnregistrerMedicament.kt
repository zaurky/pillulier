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
 * Ecrit la fiche du medicament — identite, conditionnement, stock, seuils —
 * sans toucher a ses ordonnances, puis rearme : le nom figure dans les
 * notifications, et `critique` choisit entre notification et alarme.
 */
class EnregistrerFicheMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(medicament: Medicament): Long {
        require(medicament.nom.isNotBlank()) { "le nom du medicament est obligatoire" }
        require(medicament.unitesParBoite > 0) { "une boite contient au moins une unite" }
        // Reprise de CorrigerStock, seul chemin qui ecrivait le stock librement
        // avant que l ecran Stock n edite la fiche entiere.
        require(medicament.stockUnites >= 0.0) {
            "un stock ne peut pas etre negatif, recu ${medicament.stockUnites}"
        }

        val id = medicaments.enregistrer(medicament)

        reArmerRappels()
        rafraichirWidget()
        return id
    }
}

/**
 * Ecrit une nouvelle version de prescription a partir de [dateEffet], puis
 * rearme : toute modification de posologie doit se refleter tout de suite
 * dans les alarmes.
 */
class EnregistrerPrescription @Inject constructor(
    private val ordonnances: DepotOrdonnances,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ) {
        validerPrescription(type, dateDebut, dateFin, doses)

        ordonnances.enregistrerVersion(
            medicamentId = medicamentId,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = medicamentId,
                type = type,
                rythme = rythme,
                dateDebut = dateEffet,
                dateFin = dateFin,
                dateAncrage = dateEffet,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
            dateEffet = dateEffet,
        )

        reArmerRappels()
        rafraichirWidget()
    }
}

/**
 * Cree un medicament d un bloc : sa fiche et sa premiere prescription. Toutes
 * les regles passent avant la premiere ecriture — composer naivement les deux
 * feuilles insererait la fiche avant de refuser une ordonnance sans dose, et
 * laisserait un medicament sans prescription.
 */
class EnregistrerMedicament @Inject constructor(
    medicaments: DepotMedicaments,
    ordonnances: DepotOrdonnances,
    reArmerRappels: ReArmerRappels,
    rafraichirWidget: RafraichirWidget,
) {
    private val fiche = EnregistrerFicheMedicament(medicaments, reArmerRappels, rafraichirWidget)
    private val prescription = EnregistrerPrescription(ordonnances, reArmerRappels, rafraichirWidget)

    suspend operator fun invoke(
        medicament: Medicament,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ): Long {
        // Le garde ne tient qu a la creation, seul moment ou `dateDebut` porte
        // vraiment le debut du traitement. A la modification, l ecran l a rempli
        // avec le debut de la version en vigueur : le comparer refuserait de
        // renommer un traitement date du futur, et interdirait une correction
        // retroactive remontant avant la version courante — ce que
        // `supprimerVersionsDepuis` existe precisement pour servir. Le debut du
        // traitement, lui, se corrige lui aussi retroactivement : il n offre
        // donc aucune borne inferieure a opposer a la date d effet.
        if (medicament.id == 0L) {
            require(!dateEffet.isBefore(dateDebut)) {
                "la date d effet ne peut pas preceder le debut du traitement"
            }
        }
        validerPrescription(type, dateDebut, dateFin, doses)

        val id = fiche(medicament)
        prescription(id, type, rythme, dateDebut, dateFin, doses, dateEffet)
        return id
    }
}

private fun validerPrescription(
    type: TypeOrdonnance,
    dateDebut: LocalDate,
    dateFin: LocalDate?,
    doses: List<DosePrescrite>,
) {
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
}

/**
 * Un medicament ne se supprime pas : ses prises passees sont un journal du reel
 * et doivent survivre au traitement. On ferme sa fenetre d alarmes, on clot son
 * ordonnance ce soir, puis on le marque archive.
 *
 * La fenetre est fermee **avant** le marquage, comme elle l etait avant la
 * suppression : le rearmement batit son surensemble sur `medicaments.tous()`,
 * et une alarme laissee vivante deviendrait inatteignable — indeboulonnable si
 * elle est critique.
 */
class ArchiverMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val reArmerRappels: ReArmerRappels,
    private val horloge: Horloge,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(medicamentId: Long) {
        val aujourdhui = horloge.aujourdhui()

        (1L until ReArmerRappels.JOURS_FENETRE).forEach { decalage ->
            val jour = aujourdhui.plusDays(decalage)
            Moment.entries.forEach { moment ->
                programmateur.annuler(CleRappel(medicamentId, jour, moment))
                notifications.retirer(CleRappel(medicamentId, jour, moment))
            }
        }

        ordonnances.cloturerA(medicamentId, aujourdhui)
        medicaments.archiver(medicamentId, horloge.instant())

        reArmerRappels()
        rafraichirWidget()
    }
}
