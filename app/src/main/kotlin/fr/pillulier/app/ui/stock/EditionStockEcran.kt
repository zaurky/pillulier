package fr.pillulier.app.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.ChampsFiche

@Composable
fun EditionStockEcran(
    medicamentId: Long,
    surSortie: () -> Unit,
    vue: EditionStockViewModel = hiltViewModel(),
) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LaunchedEffect(medicamentId) { vue.charger(medicamentId) }
    LaunchedEffect(etat.enregistre) { if (etat.enregistre) surSortie() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChampsFiche(
            nom = etat.nom,
            surNom = vue::modifierNom,
            dosage = etat.dosage,
            surDosage = vue::modifierDosage,
            forme = etat.forme,
            surForme = vue::modifierForme,
            unitesParBoite = etat.unitesParBoite,
            surUnitesParBoite = vue::modifierUnitesParBoite,
            stockUnites = etat.stockUnites,
            surStock = vue::modifierStock,
        )

        // Le seuil suit le type de prescription, comme a la creation : en jours
        // quand la consommation se prevoit, en unites quand elle est a la demande.
        if (etat.aLaDemande) {
            OutlinedTextField(
                value = etat.seuilAlerteUnites,
                onValueChange = vue::modifierSeuilUnites,
                label = { Text("Alerter sous X unités restantes") },
            )
        } else {
            OutlinedTextField(
                value = etat.seuilAlerteJours,
                onValueChange = vue::modifierSeuilJours,
                label = { Text("Alerter X jours avant la fin (vide = défaut)") },
            )
        }

        etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { vue.enregistrer() }) { Text("Enregistrer") }
    }
}
