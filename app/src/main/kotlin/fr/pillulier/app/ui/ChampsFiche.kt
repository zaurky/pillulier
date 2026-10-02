package fr.pillulier.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.pillulier.domain.Forme

/**
 * Identite et conditionnement d'un medicament. Emet ses champs dans la colonne
 * de l'appelant : la creation et l'ecran Stock les espacent a leur facon.
 */
@Composable
fun ChampsFiche(
    nom: String,
    surNom: (String) -> Unit,
    dosage: String,
    surDosage: (String) -> Unit,
    forme: Forme,
    surForme: (Forme) -> Unit,
    unitesParBoite: String,
    surUnitesParBoite: (String) -> Unit,
    stockUnites: String,
    surStock: (String) -> Unit,
) {
    OutlinedTextField(
        value = nom,
        onValueChange = surNom,
        label = { Text("Nom") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = dosage,
        onValueChange = surDosage,
        label = { Text("Dosage") },
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Forme", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Forme.entries.forEach { candidate ->
            FilterChip(
                selected = forme == candidate,
                onClick = { surForme(candidate) },
                label = { Text(libelleForme(candidate, 1.0)) },
            )
        }
    }

    OutlinedTextField(
        value = unitesParBoite,
        onValueChange = surUnitesParBoite,
        label = { Text("Unités par boîte") },
    )
    OutlinedTextField(
        value = stockUnites,
        onValueChange = surStock,
        label = { Text("Stock actuel en unités") },
    )
}
