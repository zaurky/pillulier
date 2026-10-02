package fr.pillulier.app.ui.stock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.couleursRenouvellement
import fr.pillulier.app.usecase.LigneStock
import java.time.format.DateTimeFormatter

private val formatDate = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun StockEcran(
    surSelection: (Long) -> Unit,
    vue: StockViewModel = hiltViewModel(),
) {
    val lignes by vue.lignes.collectAsStateWithLifecycle()
    val (planifies, aLaDemande) = lignes.partition { !it.aLaDemande }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        items(planifies) { ligne ->
            LigneStockCarte(ligne, surSelection, vue)
        }

        if (aLaDemande.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    "À la demande — consommation imprévisible",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(aLaDemande) { ligne ->
                LigneStockCarte(ligne, surSelection, vue)
            }
        }
    }
}

@Composable
private fun LigneStockCarte(ligne: LigneStock, surSelection: (Long) -> Unit, vue: StockViewModel) {
    // Le recomptage se saisit dans la fiche : un seul endroit pour une meme
    // valeur. « +1 boite » reste ici, geste d'une touche sans saisie.
    // Une boite a renouveler se lit de loin : toute la carte passe a l'orange,
    // texte et bouton compris, au lieu d'un seul libelle rouge.
    val (fondAlerte, texteAlerte) = couleursRenouvellement()
    Card(
        onClick = { surSelection(ligne.medicamentId) },
        colors = if (ligne.enAlerte) {
            CardDefaults.cardColors(containerColor = fondAlerte, contentColor = texteAlerte)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${ligne.nom} ${ligne.dosage}", style = MaterialTheme.typography.bodyLarge)
            Text(ligne.libelleUnites, style = MaterialTheme.typography.bodyMedium)

            if (ligne.joursRestants != null) {
                Text(
                    "Plus que ${ligne.joursRestants} jours · épuisement le " +
                        "${ligne.dateEpuisement?.format(formatDate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ligne.dateAlerte?.let {
                    Text("Alerte le ${it.format(formatDate)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (ligne.enAlerte) {
                Text("À renouveler", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = { vue.ajouterBoite(ligne.medicamentId) },
                modifier = Modifier.padding(top = 8.dp),
                colors = if (ligne.enAlerte) {
                    ButtonDefaults.outlinedButtonColors(contentColor = texteAlerte)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                border = if (ligne.enAlerte) BorderStroke(1.dp, texteAlerte) else ButtonDefaults.outlinedButtonBorder(),
            ) {
                Text("+1 boîte")
            }
        }
    }
}
