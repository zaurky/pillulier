package fr.pillulier.app.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import fr.pillulier.app.MainActivity
import fr.pillulier.app.ui.libelleMoment
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Les mêmes schémas de couleurs que `MainActivity` : le widget doit ressembler à l'app. */
private val couleursWidget = ColorProviders(light = lightColorScheme(), dark = darkColorScheme())

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

object PillulierWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val acces = acces(context)
        val horloge = acces.horloge()
        val journee = acces.observerJournee()(horloge.aujourdhui())

        provideContent {
            val lignes by journee.collectAsState(initial = emptyList())
            val annulable = lireAnnulable(currentState<Preferences>())

            val glanceId = LocalGlanceId.current
            val contexte = LocalContext.current

            // La fenêtre se referme d'elle-même, sans attendre une autre
            // écriture. `lignesDuWidget` revérifie l'expiration de son côté :
            // si le processus a été tué entre-temps, ce `LaunchedEffect` n'a
            // jamais tourné et l'état persisté ne doit pas ressusciter la ligne.
            LaunchedEffect(annulable) {
                val restant = annulable?.let {
                    it.expiration.toEpochMilli() - horloge.instant().toEpochMilli()
                } ?: return@LaunchedEffect
                if (restant > 0) delay(restant)
                ecrireAnnulable(contexte, glanceId, annulable = null)
                PillulierWidget.update(contexte, glanceId)
            }

            GlanceTheme(colors = couleursWidget) {
                ContenuWidget(lignesDuWidget(lignes, annulable, horloge.instant()))
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
            return@Column
        }

        LazyColumn {
            lignes.groupBy { it.moment }.forEach { (moment, duMoment) ->
                item {
                    Text(
                        "${libelleMoment(moment)} · ${duMoment.first().heure.format(formatHeure)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(duMoment) { ligne -> LigneCochable(ligne) }
            }
        }
    }
}

@Composable
private fun LigneCochable(ligne: LigneWidget) {
    val parametres = actionParametersOf(
        CLE_MEDICAMENT to ligne.medicamentId,
        CLE_MOMENT to ligne.moment.name,
        CLE_DOSE to ligne.dose,
    )

    Row(
        // L'appui ailleurs que sur la case ouvre l'app : seule la case enregistre.
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = ligne.barree,
            onCheckedChange = if (ligne.barree) {
                actionRunCallback<ActionAnnuler>(parametres)
            } else {
                actionRunCallback<ActionCocher>(parametres)
            },
            text = "${ligne.nom} ${ligne.dosage}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                textDecoration = if (ligne.barree) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        Text(
            if (ligne.barree) "Annuler" else ligne.libelleDose,
            style = TextStyle(
                color = if (ligne.enRetard) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier
                .padding(start = 8.dp)
                .let { if (ligne.barree) it.clickable(actionRunCallback<ActionAnnuler>(parametres)) else it },
        )
    }
}
