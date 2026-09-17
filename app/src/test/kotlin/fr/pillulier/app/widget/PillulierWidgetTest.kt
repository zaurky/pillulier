package fr.pillulier.app.widget

import androidx.glance.action.ActionModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.RunCallbackAction
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.MainActivity
import fr.pillulier.domain.Moment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillulierWidgetTest {

    /**
     * `hasRunCallbackClickAction` (androidx.glance.appwidget.testing.unit) ne
     * détecte qu'une `RunCallbackAction` posée directement par `.clickable(...)`.
     * La case cochable de Glance (`CheckBox.onCheckedChange`) enveloppe l'action
     * dans une `CompoundButtonAction` — la librairie de test 1.2.0 n'a pas
     * d'équivalent pour les cases, et cette enveloppe est `internal` : on ne
     * peut pas la référencer, seulement l'inspecter par réflexion, sa méthode
     * publique restant accessible côté JVM malgré la restriction Kotlin.
     */
    private fun hasCaseRunCallbackAction(
        callbackClass: Class<out ActionCallback>,
        parameters: ActionParameters,
    ) = GlanceNodeMatcher<MappedNode>(
        "has case run callback action with callback class: ${callbackClass.name} and parameters: $parameters",
    ) { node ->
        node.value.emittable.modifier.any { element ->
            val action = (element as? ActionModifier)?.action
            val interieure = action?.let {
                runCatching { it.javaClass.getMethod("getInnerAction").invoke(it) }.getOrNull()
            }
            interieure is RunCallbackAction &&
                interieure.callbackClass == callbackClass &&
                interieure.parameters == parameters
        }
    }

    private val dateDuJour = LocalDate.of(2026, 1, 5)

    private fun ligneWidget(
        id: Long = 1L,
        nom: String = "Levothyrox",
        moment: Moment = Moment.MATIN,
        barree: Boolean = false,
    ) = LigneWidget(
        medicamentId = id,
        moment = moment,
        nom = nom,
        dosage = "75 µg",
        libelleDose = "1 comprimé",
        dose = 1.0,
        heure = LocalTime.of(8, 0),
        enRetard = false,
        barree = barree,
        date = dateDuJour,
    )

    @Test
    fun `sans prise restante le widget invite a ne rien faire`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = emptyList()) }

        onNode(hasText("Rien à prendre")).assertExists()
    }

    @Test
    fun `chaque prise restante est affichee avec son moment`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ContenuWidget(
                lignes = listOf(
                    ligneWidget(id = 1L, nom = "Levothyrox", moment = Moment.MATIN),
                    ligneWidget(id = 2L, nom = "Doliprane", moment = Moment.SOIR),
                ),
            )
        }

        onNode(hasText("Levothyrox")).assertExists()
        onNode(hasText("Doliprane")).assertExists()
        onNode(hasText("Matin")).assertExists()
        onNode(hasText("Soir")).assertExists()
    }

    @Test
    fun `l appui hors de la case ouvre l application`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L))) }

        onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
    }

    @Test
    fun `cocher une prise declenche l action de coche avec le jour du rendu`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L))) }

        onNode(
            hasCaseRunCallbackAction(
                callbackClass = ActionCocher::class.java,
                parameters = actionParametersOf(
                    CLE_MEDICAMENT to 7L,
                    CLE_MOMENT to Moment.MATIN.name,
                    CLE_DATE to dateDuJour.toString(),
                ),
            ),
        ).assertExists()
    }

    @Test
    fun `une prise barree propose de l annuler`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L, barree = true))) }

        onNode(hasText("Annuler")).assertExists()
    }

    @Test
    fun `annuler une prise barree declenche l action d annulation`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L, barree = true))) }

        onNode(
            hasRunCallbackClickAction<ActionAnnuler>(
                parameters = actionParametersOf(
                    CLE_MEDICAMENT to 7L,
                    CLE_MOMENT to Moment.MATIN.name,
                    CLE_DATE to dateDuJour.toString(),
                ),
            ),
        ).assertExists()
    }
}
