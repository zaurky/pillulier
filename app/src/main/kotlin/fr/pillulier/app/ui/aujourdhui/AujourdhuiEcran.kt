package fr.pillulier.app.ui.aujourdhui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.CapsuleALaDemande
import fr.pillulier.app.ui.CapsulePrise
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.StatutPrise
import java.time.format.DateTimeFormatter

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

/**
 * La journee reprend la grammaire de la semaine : un en-tete par section et des
 * prises en capsules. Les deux ecrans disaient le meme statut de deux facons,
 * une teinte ici et une phrase la.
 */
@Composable
fun AujourdhuiEcran(vue: AujourdhuiViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (etat.alertes.isNotEmpty()) {
            item {
                Section("À renouveler") {
                    etat.alertes.forEach { alerte ->
                        Text("${alerte.nom} : ${alerte.message}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        etat.lignes.groupBy { it.moment }.forEach { (moment, lignes) ->
            item {
                Section("${libelleMoment(moment)} · ${lignes.first().heure.format(formatHeure)}") {
                    Capsules {
                        lignes.forEach { ligne ->
                            CapsulePrise(
                                nom = "${ligne.nom} ${ligne.dosage}",
                                statut = ligne.statut,
                                detail = ligne.libelleDose,
                                // Une prise faite ou manquee est close : seule
                                // une prise encore ouverte se coche.
                                onClick = if (ligne.statut == StatutPrise.PRISE ||
                                    ligne.statut == StatutPrise.OUBLIEE
                                ) {
                                    null
                                } else {
                                    { vue.cocher(ligne) }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (etat.aLaDemande.isNotEmpty()) {
            item {
                Section("À la demande") {
                    Capsules {
                        etat.aLaDemande.forEach { medicament ->
                            CapsuleALaDemande(nom = "${medicament.nom} ${medicament.dosage}") {
                                vue.enregistrerALaDemande(medicament.id, 1.0)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(titre, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        contenu()
    }
}

// FlowRow reste marque experimental dans le BOM 2025.03.00, son API est
// stable dans les faits depuis foundation 1.7.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Capsules(contenu: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        contenu()
    }
}
