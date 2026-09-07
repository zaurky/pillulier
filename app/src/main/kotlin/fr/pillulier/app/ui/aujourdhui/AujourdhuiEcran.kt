package fr.pillulier.app.ui.aujourdhui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.app.ui.libelleStatut
import fr.pillulier.domain.StatutPrise
import java.time.format.DateTimeFormatter

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun AujourdhuiEcran(vue: AujourdhuiViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (etat.alertes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("À renouveler", style = MaterialTheme.typography.titleMedium)
                        etat.alertes.forEach { alerte ->
                            Text("${alerte.nom} : ${alerte.message}")
                        }
                    }
                }
            }
        }

        etat.lignes.groupBy { it.moment }.forEach { (moment, lignes) ->
            item {
                Text(
                    "${libelleMoment(moment)} · ${lignes.first().heure.format(formatHeure)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(lignes) { ligne ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = ligne.statut == StatutPrise.PRISE,
                        enabled = ligne.statut != StatutPrise.PRISE && ligne.statut != StatutPrise.OUBLIEE,
                        onCheckedChange = { vue.cocher(ligne) },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${ligne.nom} ${ligne.dosage}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${ligne.libelleDose} · ${libelleStatut(ligne.statut)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (etat.aLaDemande.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("À la demande", style = MaterialTheme.typography.titleMedium)
            }

            items(etat.aLaDemande) { medicament ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${medicament.nom} ${medicament.dosage}",
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = { vue.enregistrerALaDemande(medicament.id, 1.0) }) {
                        Text("J'en ai pris 1")
                    }
                }
            }
        }
    }
}
