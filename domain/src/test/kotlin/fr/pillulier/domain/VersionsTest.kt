package fr.pillulier.domain

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionsTest {

    private fun version(
        id: Long,
        debut: LocalDate,
        fin: LocalDate?,
        dose: Double,
    ) = OrdonnanceAvecDoses(
        ordonnance = Ordonnance(
            id = id,
            medicamentId = 1,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = debut,
            dateFin = fin,
            dateAncrage = LocalDate.of(2026, 1, 1),
        ),
        doses = listOf(DosePrescrite(Moment.MATIN, dose)),
    )

    private val versions = listOf(
        version(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), dose = 1.0),
        version(2, LocalDate.of(2026, 1, 10), null, dose = 2.0),
    )

    @Test
    fun `rend la version qui couvre la date`() {
        assertEquals(1L, versions.enVigueur(1, LocalDate.of(2026, 1, 5))?.ordonnance?.id)
        assertEquals(2L, versions.enVigueur(1, LocalDate.of(2026, 1, 20))?.ordonnance?.id)
    }

    @Test
    fun `rend la derniere version anterieure quand aucune ne couvre la date`() {
        val closes = listOf(
            version(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), dose = 1.0),
            version(2, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15), dose = 2.0),
        )

        assertEquals(2L, closes.enVigueur(1, LocalDate.of(2026, 2, 1))?.ordonnance?.id)
    }

    @Test
    fun `rend la premiere version quand la date precede tout`() {
        assertEquals(1L, versions.enVigueur(1, LocalDate.of(2025, 12, 1))?.ordonnance?.id)
    }

    @Test
    fun `rend null pour un medicament sans ordonnance`() {
        assertNull(versions.enVigueur(medicamentId = 99, date = LocalDate.of(2026, 1, 5)))
    }
}
