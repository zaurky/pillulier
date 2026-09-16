package fr.pillulier.app.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.pillulier.app.MainActivity

/** Les mêmes schémas de couleurs que `MainActivity` : le widget doit ressembler à l'app. */
private val couleursWidget = ColorProviders(light = lightColorScheme(), dark = darkColorScheme())

object PillulierWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = couleursWidget) {
                ContenuWidget(lignes = emptyList())
            }
        }
    }
}

@Composable
fun ContenuWidget(lignes: List<LigneWidget>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        if (lignes.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Rien à prendre",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}
