package fr.pillulier.app.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillulierWidgetTest {

    @Test
    fun `sans prise restante le widget invite a ne rien faire`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = emptyList()) }

        onNode(hasText("Rien à prendre")).assertExists()
    }
}
