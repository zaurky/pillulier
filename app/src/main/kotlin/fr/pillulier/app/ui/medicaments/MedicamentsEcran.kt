package fr.pillulier.app.ui.medicaments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.formaterDose

@Composable
fun MedicamentsEcran(
    surSelection: (Long) -> Unit,
    vue: MedicamentsViewModel = hiltViewModel(),
) {
    val medicaments by vue.medicaments.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(medicaments) { medicament ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { surSelection(medicament.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text("${medicament.nom} ${medicament.dosage}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formaterDose(medicament.stockUnites)} en stock" +
                            if (medicament.critique) " · critique" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { surSelection(0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text("+")
        }
    }
}
