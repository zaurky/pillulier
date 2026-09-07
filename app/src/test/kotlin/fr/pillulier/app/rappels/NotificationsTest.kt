package fr.pillulier.app.rappels

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationsTest {

    private lateinit var contexte: Context
    private lateinit var notifications: Notifications
    private lateinit var gestionnaire: NotificationManager

    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext()
        notifications = Notifications(contexte)
        gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        notifications.creerCanaux()
    }

    private fun medicament(critique: Boolean = false) = Medicament(
        id = 7,
        nom = "Levothyrox",
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = critique,
    )

    @Test
    fun `les deux canaux sont crees`() {
        assertNotNull(gestionnaire.getNotificationChannel(CANAL_RAPPELS))
        assertNotNull(gestionnaire.getNotificationChannel(CANAL_CRITIQUES))
    }

    @Test
    fun `un rappel est poste sous l identifiant de sa cle`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(cle, medicament(), dose = 0.5, critique = false)

        val postees = shadowOf(gestionnaire).allNotifications
        assertTrue(postees.isNotEmpty())
        assertTrue(shadowOf(gestionnaire).activeNotifications.any { it.id == cle.codeRequete() })
    }

    @Test
    fun `deux medicaments du meme moment donnent deux notifications distinctes`() {
        val matinA = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        val matinB = CleRappel(8, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(matinA, medicament(), dose = 1.0, critique = false)
        notifications.posterRappel(matinB, medicament().copy(id = 8, nom = "Kardegic"), dose = 1.0, critique = false)

        val identifiants = shadowOf(gestionnaire).activeNotifications.map { it.id }.toSet()
        assertTrue(matinA.codeRequete() in identifiants)
        assertTrue(matinB.codeRequete() in identifiants)
    }

    @Test
    fun `retirer un rappel enleve sa notification`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        notifications.posterRappel(cle, medicament(), dose = 1.0, critique = false)

        notifications.retirer(cle)

        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
        )
    }

    @Test
    fun `un medicament critique passe par le canal critiques`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(cle, medicament(critique = true), dose = 1.0, critique = true)

        val postee = shadowOf(gestionnaire).activeNotifications.first { it.id == cle.codeRequete() }
        assertEquals(CANAL_CRITIQUES, postee.notification.channelId)
    }
}
