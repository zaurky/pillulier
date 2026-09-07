package fr.pillulier.app.ui

import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import org.junit.Test
import java.time.DayOfWeek
import kotlin.test.assertEquals

class LibellesTest {

    @Test
    fun `une demi unite s ecrit en fraction`() {
        assertEquals("½", formaterDose(0.5))
        assertEquals("1½", formaterDose(1.5))
        assertEquals("2½", formaterDose(2.5))
    }

    @Test
    fun `une dose entiere s ecrit sans decimale`() {
        assertEquals("1", formaterDose(1.0))
        assertEquals("2", formaterDose(2.0))
        assertEquals("10", formaterDose(10.0))
    }

    @Test
    fun `une dose exotique garde une decimale`() {
        assertEquals("0,25", formaterDose(0.25))
        assertEquals("1,75", formaterDose(1.75))
    }

    @Test
    fun `la forme s accorde au pluriel au dela d une unite`() {
        assertEquals("comprimé", libelleForme(Forme.COMPRIME, 1.0))
        assertEquals("comprimé", libelleForme(Forme.COMPRIME, 0.5))
        assertEquals("comprimés", libelleForme(Forme.COMPRIME, 2.0))
        assertEquals("gélules", libelleForme(Forme.GELULE, 3.0))
        assertEquals("sachet", libelleForme(Forme.SACHET, 1.0))
        assertEquals("injections", libelleForme(Forme.INJECTION, 2.0))
    }

    @Test
    fun `la dose complete associe le nombre et la forme`() {
        assertEquals("½ comprimé", libelleDoseComplete(0.5, Forme.COMPRIME))
        assertEquals("2 gélules", libelleDoseComplete(2.0, Forme.GELULE))
    }

    @Test
    fun `les moments ont un libelle francais`() {
        assertEquals("Matin", libelleMoment(Moment.MATIN))
        assertEquals("Midi", libelleMoment(Moment.MIDI))
        assertEquals("Soir", libelleMoment(Moment.SOIR))
        assertEquals("Coucher", libelleMoment(Moment.COUCHER))
    }

    @Test
    fun `les jours de la semaine ont une abreviation francaise`() {
        assertEquals("Lu", libelleJour(DayOfWeek.MONDAY))
        assertEquals("Ma", libelleJour(DayOfWeek.TUESDAY))
        assertEquals("Me", libelleJour(DayOfWeek.WEDNESDAY))
        assertEquals("Je", libelleJour(DayOfWeek.THURSDAY))
        assertEquals("Ve", libelleJour(DayOfWeek.FRIDAY))
        assertEquals("Sa", libelleJour(DayOfWeek.SATURDAY))
        assertEquals("Di", libelleJour(DayOfWeek.SUNDAY))
    }
}
