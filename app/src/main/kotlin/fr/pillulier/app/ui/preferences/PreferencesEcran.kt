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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
