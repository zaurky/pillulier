package fr.pillulier.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fond et texte d'une boite a renouveler, sur la journee comme au stock.
 * Un orange a part, plus franc que l'ambre « en retard » de Capsule : une prise
 * en retard et une boite qui s'epuise ne demandent pas le meme geste. En sombre,
 * un fond plus clair qu'attendu : plus fonce, l'orange virait au brun (contraste
 * du texte 5,2:1).
 */
@Composable
fun couleursRenouvellement(): Pair<Color, Color> =
    if (isSystemInDarkTheme()) Color(0xFF9A4A00) to Color(0xFFFFE6D2) else Color(0xFFFFD3B0) to Color(0xFF4A1F00)
