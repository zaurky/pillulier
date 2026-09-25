package fr.pillulier.app.data

import fr.pillulier.app.data.db.DosePrescriteEntity
import fr.pillulier.app.data.db.OrdonnanceAvecDosesEntity
import fr.pillulier.app.data.db.OrdonnanceEntity
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals

class MappeursTest {

    @Test
    fun `tous les jours fait l aller retour`() {
        val (type, jours, n) = Rythme.TousLesJours.versColonnes()
        assertEquals(Rythme.TousLesJours, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `les jours de semaine font l aller retour`() {
        val rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val (type, jours, n) = rythme.versColonnes()
        assertEquals(rythme, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `un jour sur N fait l aller retour`() {
        val rythme = Rythme.UnJourSurN(3)
        val (type, jours, n) = rythme.versColonnes()
        assertEquals(rythme, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `une ordonnance persistee revient en objet du domaine`() {
        val entite = OrdonnanceAvecDosesEntity(
            ordonnance = OrdonnanceEntity(
                id = 4,
                medicamentId = 10,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "UN_JOUR_SUR_N",
                rythmeJours = null,
                rythmeN = 2,
                // L ancrage precede le debut de cette version : une version
                // ouverte en cours de route herite de l origine du rythme. Les
                // deux dates doivent diverger, sinon un mappeur qui lirait
                // dateDebut a la place de dateAncrage passerait par hasard.
                dateDebut = LocalDate.of(2026, 1, 5),
                dateFin = LocalDate.of(2026, 1, 10),
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = listOf(DosePrescriteEntity(1, 4, Moment.MATIN, 1.5)),
        )

        val domaine = entite.versDomaine()

        assertEquals(Rythme.UnJourSurN(2), domaine.ordonnance.rythme)
        assertEquals(LocalDate.of(2026, 1, 5), domaine.ordonnance.dateDebut)
        assertEquals(LocalDate.of(2026, 1, 10), domaine.ordonnance.dateFin)
        assertEquals(LocalDate.of(2026, 1, 1), domaine.ordonnance.dateAncrage)
        assertEquals(1.5, domaine.doses.single().dose)
        assertEquals(Moment.MATIN, domaine.doses.single().moment)
    }

    @Test
    fun `une ordonnance du domaine part en base avec son ancrage`() {
        val ordonnance = Ordonnance(
            id = 4,
            medicamentId = 10,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.UnJourSurN(2),
            // Meme divergence dans l autre sens : l ancrage ne doit pas etre
            // recopie depuis le debut de la version.
            dateDebut = LocalDate.of(2026, 1, 5),
            dateFin = LocalDate.of(2026, 1, 10),
            dateAncrage = LocalDate.of(2026, 1, 1),
        )

        val entite = ordonnance.versEntite()

        assertEquals(LocalDate.of(2026, 1, 5), entite.dateDebut)
        assertEquals(LocalDate.of(2026, 1, 10), entite.dateFin)
        assertEquals(LocalDate.of(2026, 1, 1), entite.dateAncrage)
    }
}
