package fr.pillulier.app.ui.semaine

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.app.ui.libelleStatut
import fr.pillulier.domain.StatutPrise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatJour = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/**
 * Les sept jours empiles verticalement, chacun ne montrant que ses moments
 * renseignes. La grille precedente mettait les jours en colonnes et defilait
 * horizontalement : quatre jours sur sept restaient hors ecran, sans rien qui
 * le signale.
 */
@Composable
fun SemaineEcran(vue: SemaineViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()
    val aujourdhui = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { vue.semainePrecedente() }) { Text("← Semaine") }
            TextButton(onClick = { vue.semaineSuivante() }) { Text("Semaine →") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(etat.jours, key = { it.toEpochDay() }) { jour ->
                BlocJour(jour = jour, lignes = lignesDuJour(etat, jour), estAujourdhui = jour == aujourdhui)
            }
        }
    }
}

// FlowRow reste marque experimental dans le BOM 2025.03.00, son API est
// stable dans les faits depuis foundation 1.7.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlocJour(jour: LocalDate, lignes: List<LigneJour>, estAujourdhui: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            jour.format(formatJour).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (estAujourdhui) FontWeight.Bold else FontWeight.Normal,
            color = if (estAujourdhui) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

        if (lignes.isEmpty()) {
            // Un jour vide doit se distinguer d'un jour absent de l'affichage.
            Text(
                "aucune prise prévue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        lignes.forEach { ligne ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    libelleMoment(ligne.moment),
                    modifier = Modifier.width(76.dp).padding(top = 6.dp, start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ligne.entrees.forEach { entree -> Capsule(entree.nom, entree.statut) }
                }
            }
        }
    }
}

/**
 * La teinte porte le statut d'un coup d'oeil, le marqueur le redit pour qui ne
 * distingue pas les couleurs, et la description vocale garde le statut en
 * toutes lettres.
 */
@Composable
private fun Capsule(nom: String, statut: StatutPrise) {
    val (fond, texte) = couleursStatut(statut)

    Surface(
        shape = RoundedCornerShape(50),
        color = fond,
        contentColor = texte,
        modifier = Modifier.semantics { contentDescription = "$nom — ${libelleStatut(statut)}" },
    ) {
        Text(
            "${marqueur(statut)} $nom",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * Material 3 n'a de role ni pour « fait » ni pour « en retard » : ces deux
 * paires sont explicites, les deux autres suivent le theme.
 */
@Composable
private fun couleursStatut(statut: StatutPrise): Pair<Color, Color> {
    val sombre = isSystemInDarkTheme()

    return when (statut) {
        StatutPrise.PRISE ->
            if (sombre) Color(0xFF1B4D2E) to Color(0xFFB6F2C8) else Color(0xFFCDEFD8) to Color(0xFF14421F)

        StatutPrise.EN_RETARD ->
            if (sombre) Color(0xFF5A3D00) to Color(0xFFFFDDA8) else Color(0xFFFFE6B8) to Color(0xFF4A3000)

        StatutPrise.OUBLIEE ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        StatutPrise.A_VENIR ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun marqueur(statut: StatutPrise): String = when (statut) {
    StatutPrise.PRISE -> "✓"
    StatutPrise.OUBLIEE -> "✗"
    StatutPrise.EN_RETARD -> "!"
    StatutPrise.A_VENIR -> "·"
}
