package fr.pillulier.app.ui.semaine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatJour = DateTimeFormatter.ofPattern("EEE dd", Locale.FRENCH)

@Composable
fun SemaineEcran(vue: SemaineViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { vue.semainePrecedente() }) { Text("← Semaine") }
            TextButton(onClick = { vue.semaineSuivante() }) { Text("Semaine →") }
        }

        // La grille peut dépasser la largeur de l'écran : elle défile
        // horizontalement plutôt que de comprimer les colonnes.
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Text("", modifier = Modifier.width(72.dp))
                etat.jours.forEach { jour ->
                    Text(
                        jour.format(formatJour),
                        modifier = Modifier.width(96.dp).padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            HorizontalDivider()

            Moment.entries.forEach { moment ->
                Row {
                    Text(
                        libelleMoment(moment),
                        modifier = Modifier.width(72.dp).padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )

                    etat.jours.forEach { jour ->
                        Column(modifier = Modifier.width(96.dp).padding(4.dp)) {
                            etat.cellules[jour to moment].orEmpty().forEach { entree ->
                                Text(
                                    "${marqueur(entree.statut)} ${entree.nom}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun marqueur(statut: StatutPrise): String = when (statut) {
    StatutPrise.PRISE -> "✓"
    StatutPrise.OUBLIEE -> "✗"
    StatutPrise.EN_RETARD -> "!"
    StatutPrise.A_VENIR -> "·"
}
