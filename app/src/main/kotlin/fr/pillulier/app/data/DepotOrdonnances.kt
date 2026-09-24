package fr.pillulier.app.data

import androidx.room.withTransaction
import fr.pillulier.app.data.db.OrdonnanceDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import fr.pillulier.domain.enVigueur
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotOrdonnances @Inject constructor(
    private val dao: OrdonnanceDao,
    private val base: PillulierDatabase,
) {

    fun observerToutes(): Flow<List<OrdonnanceAvecDoses>> =
        dao.observerToutes().map { entites -> entites.map { it.versDomaine() } }

    suspend fun toutes(): List<OrdonnanceAvecDoses> = dao.toutes().map { it.versDomaine() }

    /** La version active a cette date, ou la plus proche — voir `enVigueur`. */
    suspend fun enVigueur(medicamentId: Long, date: LocalDate): OrdonnanceAvecDoses? =
        dao.versionsDe(medicamentId).map { it.versDomaine() }.enVigueur(medicamentId, date)

    suspend fun versionsDe(medicamentId: Long): List<OrdonnanceAvecDoses> =
        dao.versionsDe(medicamentId).map { it.versDomaine() }

    /** Ferme toutes les versions encore ouvertes au-dela de [dateFin]. */
    suspend fun cloturerA(medicamentId: Long, dateFin: LocalDate) =
        dao.cloturerOuvertes(medicamentId, dateFin)

    /**
     * La nouvelle version prend tout a partir de [dateEffet] : les versions
     * anterieures sont cloturees la veille, celles qui commencent a cette date
     * ou apres disparaissent. Une seule regle, qui couvre la creation, la
     * modification du jour et la correction retroactive.
     *
     * Rien n'est ecrit si la version en vigueur prescrit deja exactement la
     * meme chose : renommer un medicament ne doit pas couper son historique.
     */
    suspend fun enregistrerVersion(
        medicamentId: Long,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
        dateEffet: LocalDate,
    ) {
        val existantes = versionsDe(medicamentId)
        val courante = existantes.enVigueur(medicamentId, dateEffet)

        if (courante != null && prescritLaMemeChose(courante, ordonnance, doses)) {
            // Un renommage seul ne doit rien ecrire, mais la regle reste « tout
            // a partir de dateEffet » : si une AUTRE version plus tardive
            // existe deja, elle doit disparaitre meme quand la version en
            // vigueur ne change pas. `courante` elle-meme demarre forcement a
            // ou avant dateEffet ; l exclure evite de la prendre pour la
            // version tardive qu il faudrait effacer, quand elle demarre
            // exactement a dateEffet.
            val rienAEffacer = existantes.none {
                it.ordonnance.id != courante.ordonnance.id && it.ordonnance.dateDebut >= dateEffet
            }
            if (rienAEffacer) return
        }

        val ancrage = courante?.ordonnance?.dateAncrage?.coerceAtMost(dateEffet) ?: dateEffet

        // Quatre ecritures qui doivent tenir ou tomber ensemble : un plantage
        // entre la cloture et l insertion laisserait le medicament sans
        // prescription. `@Transaction` de Room ne s applique qu aux methodes de
        // DAO — hors DAO il ne fait rien — d ou `withTransaction`.
        base.withTransaction {
            dao.supprimerVersionsDepuis(medicamentId, dateEffet)
            dao.cloturerVersionsAvant(medicamentId, dateEffet, dateEffet.minusDays(1))

            val id = dao.insererOrdonnance(
                ordonnance.copy(
                    id = 0,
                    medicamentId = medicamentId,
                    dateDebut = dateEffet,
                    dateAncrage = ancrage,
                ).versEntite(),
            )
            dao.insererDoses(doses.map { it.versEntite(id) })
        }
    }

    private fun prescritLaMemeChose(
        courante: OrdonnanceAvecDoses,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
    ): Boolean =
        courante.ordonnance.type == ordonnance.type &&
            courante.ordonnance.rythme == ordonnance.rythme &&
            courante.ordonnance.dateFin == ordonnance.dateFin &&
            courante.doses.sortedBy { it.moment } == doses.sortedBy { it.moment }
}
