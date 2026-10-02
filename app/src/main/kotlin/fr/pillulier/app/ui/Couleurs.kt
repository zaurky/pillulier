package fr.pillulier.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fond et texte d'une boite a renouveler, sur la journee comme au stock.
 * Un orange a part, plus franc que l'ambre « en retard » de Capsule : une prise
 * en retard et une boite qui s'epuise ne demandent pas le meme geste.
 */
@Composable
fun couleursRenouvellement(): Pair<Color, Color> =
    if (isSystemInDarkTheme()) Color(0xFF5C2A00) to Color(0xFFFFD3B0) else Color(0xFFFFD3B0) to Color(0xFF4A1F00)
