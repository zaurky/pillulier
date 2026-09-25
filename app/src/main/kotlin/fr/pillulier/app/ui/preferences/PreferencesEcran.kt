package fr.pillulier.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
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
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Heures des moments", style = MaterialTheme.typography.titleMedium)

        Moment.entries.forEach { moment ->
            val heure = etat.heures[moment] ?: return@forEach

            ReglageStepper(
                libelle = libelleMoment(moment),
                valeur = heure.format(formatHeure),
                libelleMoins = "−15 min",
                libellePlus = "+15 min",
                surMoins = { vue.definirHeure(moment, heure.minusMinutes(15)) },
                surPlus = { vue.definirHeure(moment, heure.plusMinutes(15)) },
            )
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

        ReglageStepper(
            libelle = "Alerter par défaut",
            valeur = "${etat.valeurs.seuilAlerteJoursDefaut} jours avant la fin",
            libelleMoins = "−",
            libellePlus = "+",
            surMoins = { vue.definirSeuilJours(etat.valeurs.seuilAlerteJoursDefaut - 1) },
            surPlus = { vue.definirSeuilJours(etat.valeurs.seuilAlerteJoursDefaut + 1) },
        )

        SectionJournal(vue) // JOURNAL-DEBUG
    }
}

/**
 * TEMPORAIRE — JOURNAL-DEBUG : toute cette section part avec le journal de
 * diagnostic, une fois les deux bugs confirmés corrigés sur l'appareil.
 */
@Composable
private fun SectionJournal(vue: PreferencesViewModel) {
    val journal by vue.journalAffiche.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text("Diagnostic (temporaire)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Journal des coches, alarmes et rappels. À retirer une fois les deux bugs confirmés corrigés.",
        style = MaterialTheme.typography.bodySmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { if (journal == null) vue.afficherJournal() else vue.masquerJournal() },
        ) {
            Text(if (journal == null) "Afficher" else "Masquer")
        }
        OutlinedButton(
            onClick = {
                portee.launch {
                    val envoi = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, vue.uriDePartage())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    contexte.startActivity(Intent.createChooser(envoi, "Partager le journal"))
                }
            },
        ) { Text("Partager") }
        OutlinedButton(onClick = { vue.effacerJournal() }) { Text("Effacer") }
    }

    journal?.let { texte ->
        // Les lignes sont longues et horodatées : elles ne doivent ni se
        // replier ni se faire tronquer, sinon le journal devient illisible.
        Text(
            texte,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            textAlign = TextAlign.Start,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun ReglageMinutes(libelle: String, valeur: Int, surChangement: (Int) -> Unit) {
    ReglageStepper(
        libelle = libelle,
        valeur = "$valeur min",
        libelleMoins = "−5",
        libellePlus = "+5",
        surMoins = { surChangement(valeur - 5) },
        surPlus = { surChangement(valeur + 5) },
    )
}

/**
 * Le libelle prend sa propre ligne : mis a cote du stepper, les plus longs
 * — « Délai du « Plus tard » », « 3 jours avant la fin » — debordaient et se
 * faisaient tronquer. Le ViewModel borne les valeurs, l'ecran ne fait plus
 * que compter.
 */
@Composable
private fun ReglageStepper(
    libelle: String,
    valeur: String,
    libelleMoins: String,
    libellePlus: String,
    surMoins: () -> Unit,
    surPlus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(libelle, style = MaterialTheme.typography.bodyMedium)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = surMoins) { Text(libelleMoins) }
            Text(
                valeur,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            OutlinedButton(onClick = surPlus) { Text(libellePlus) }
        }
    }
}
