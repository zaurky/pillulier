package fr.pillulier.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Moment
import java.time.format.DateTimeFormatter

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun PreferencesEcran(vue: PreferencesViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Heures des moments", style = MaterialTheme.typography.titleMedium)

        Moment.entries.forEach { moment ->
            val heure = etat.heures[moment] ?: return@forEach

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(libelleMoment(moment), modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(onClick = { vue.definirHeure(moment, heure.minusMinutes(15)) }) {
                    Text("−15 min")
                }
                Text(heure.format(formatHeure), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { vue.definirHeure(moment, heure.plusMinutes(15)) }) {
                    Text("+15 min")
                }
            }
        }

        Text("Rappels", style = MaterialTheme.typography.titleMedium)

        ReglageMinutes(
            libelle = "Délai du « Plus tard »",
            valeur = etat.valeurs.delaiPlusTardMinutes,
            surChangement = vue::definirDelaiPlusTard,
        )
        ReglageMinutes(
            libelle = "Intervalle de relance",
            valeur = etat.valeurs.intervalleRelanceMinutes,
            surChangement = vue::definirIntervalleRelance,
        )

        Text("Renouvellement", style = MaterialTheme.typography.titleMedium)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Alerter par défaut")
            OutlinedButton(
                onClick = { vue.definirSeuilJours((etat.valeurs.seuilAlerteJoursDefaut - 1).coerceAtLeast(1)) },
            ) {
                Text("−")
            }
            Text("${etat.valeurs.seuilAlerteJoursDefaut} jours avant la fin")
            OutlinedButton(onClick = { vue.definirSeuilJours(etat.valeurs.seuilAlerteJoursDefaut + 1) }) {
                Text("+")
            }
        }
    }
}

@Composable
private fun ReglageMinutes(libelle: String, valeur: Int, surChangement: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(libelle)
        OutlinedButton(onClick = { surChangement((valeur - 5).coerceAtLeast(5)) }) { Text("−5") }
        Text("$valeur min")
        OutlinedButton(onClick = { surChangement(valeur + 5) }) { Text("+5") }
    }
}
