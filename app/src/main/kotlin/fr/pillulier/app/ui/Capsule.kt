package fr.pillulier.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.pillulier.domain.StatutPrise

/**
 * Une prise en capsule, partagee par la semaine et la journee : les deux ecrans
 * disaient le meme statut dans deux grammaires differentes, la teinte d'un cote
 * et une phrase de l'autre.
 *
 * La teinte porte le statut d'un coup d'oeil, le marqueur le redit pour qui ne
 * distingue pas les couleurs, et la description vocale garde le statut en
 * toutes lettres.
 *
 * `detail` complete le nom sur la journee, ou la dose a sa place ; la semaine
 * l'omet, faute de largeur pour sept jours.
 */
@Composable
fun CapsulePrise(
    nom: String,
    statut: StatutPrise,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val (fond, texte) = couleursStatut(statut)
    val libelle = if (detail == null) nom else "$nom · $detail"

    Capsule(
        contenu = "${marqueur(statut)} $libelle",
        fond = fond,
        texte = texte,
        description = "$libelle — ${libelleStatut(statut)}",
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Un medicament a la demande : aucun statut a porter, seulement le geste. Le
 * `+` le distingue des quatre marqueurs de statut.
 */
@Composable
fun CapsuleALaDemande(nom: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Capsule(
        contenu = "+ $nom",
        fond = MaterialTheme.colorScheme.secondaryContainer,
        texte = MaterialTheme.colorScheme.onSecondaryContainer,
        description = "$nom — enregistrer une prise",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun Capsule(
    contenu: String,
    fond: Color,
    texte: Color,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val corps: @Composable () -> Unit = {
        Text(
            contenu,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    val forme = RoundedCornerShape(50)
    val semantique = modifier.semantics { contentDescription = description }

    if (onClick == null) {
        Surface(shape = forme, color = fond, contentColor = texte, modifier = semantique) { corps() }
    } else {
        Surface(
            onClick = onClick,
            shape = forme,
            color = fond,
            contentColor = texte,
            modifier = semantique,
        ) { corps() }
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
