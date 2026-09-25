package fr.pillulier.app.ui.medicaments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.formaterDose
import fr.pillulier.app.ui.libelleForme
import fr.pillulier.app.ui.libelleJour
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MILLIS_PAR_JOUR = 86_400_000L

@Composable
fun EditionEcran(
    medicamentId: Long,
    surSortie: () -> Unit,
    vue: EditionViewModel = hiltViewModel(),
) {
    val etat by vue.etat.collectAsStateWithLifecycle()
    var confirmationSuppression by remember { mutableStateOf(false) }

    LaunchedEffect(medicamentId) { vue.charger(medicamentId) }
    LaunchedEffect(etat.enregistre) { if (etat.enregistre) surSortie() }

    // Un médicament ne se supprime pas : il s'archive. Son historique de
    // prises reste lisible, seul son planning à venir s'arrête.
    if (confirmationSuppression) {
        AlertDialog(
            onDismissRequest = { confirmationSuppression = false },
            title = { Text("Archiver ce médicament ?") },
            text = {
                Text(
                    "« ${etat.nom} » sera archivé : il disparaîtra de la liste et des " +
                        "prochains jours. Ses prises déjà enregistrées restent dans l'historique.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmationSuppression = false
                        vue.archiver()
                    },
                ) {
                    Text("Archiver")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationSuppression = false }) { Text("Annuler") }
            },
        )
    }

    // Dater un effet avant la plus ancienne prescription efface toutes les
    // versions d'un coup. Le geste est legitime — la spec autorise la
    // correction retroactive — mais une annee mal tapee dans le selecteur en
    // est indiscernable, alors on montre ce qui disparaitrait.
    etat.versionsAEffacer?.let { combien ->
        AlertDialog(
            onDismissRequest = vue::renoncerEffacement,
            title = { Text("Remonter avant le début du traitement ?") },
            text = {
                Text(
                    "Cette date d'effet est antérieure à la plus ancienne ordonnance " +
                        "enregistrée. " +
                        if (combien == 1) {
                            "La prescription actuelle sera remplacée par celle-ci."
                        } else {
                            "Les $combien versions de prescription seront remplacées par celle-ci."
                        } +
                        " Vos prises déjà enregistrées ne sont pas touchées.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vue.confirmerEffacement() }) { Text("Remplacer") }
            },
            dismissButton = {
                TextButton(onClick = vue::renoncerEffacement) { Text("Annuler") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = etat.nom,
            onValueChange = vue::modifierNom,
            label = { Text("Nom") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = etat.dosage,
            onValueChange = vue::modifierDosage,
            label = { Text("Dosage") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Forme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Forme.entries.forEach { forme ->
                FilterChip(
                    selected = etat.forme == forme,
                    onClick = { vue.modifierForme(forme) },
                    label = { Text(libelleForme(forme, 1.0)) },
                )
            }
        }

        OutlinedTextField(
            value = etat.unitesParBoite,
            onValueChange = vue::modifierUnitesParBoite,
            label = { Text("Unités par boîte") },
        )
        OutlinedTextField(
            value = etat.stockUnites,
            onValueChange = vue::modifierStock,
            label = { Text("Stock actuel en unités") },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = etat.critique, onCheckedChange = vue::modifierCritique)
            Text("Prise critique (alarme plein écran)", modifier = Modifier.padding(start = 8.dp))
        }

        Text("Type d'ordonnance", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeOrdonnance.entries.forEach { type ->
                FilterChip(
                    selected = etat.type == type,
                    onClick = { vue.modifierType(type) },
                    label = { Text(if (type == TypeOrdonnance.PLANIFIEE) "Planifiée" else "À la demande") },
                )
            }
        }

        if (etat.type == TypeOrdonnance.PLANIFIEE) {
            Text("Rythme", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = etat.rythme is Rythme.TousLesJours,
                    onClick = { vue.choisirTousLesJours() },
                    label = { Text("Tous les jours") },
                )
                FilterChip(
                    selected = etat.rythme is Rythme.UnJourSurN,
                    onClick = { vue.choisirUnJourSurN() },
                    label = { Text("Un jour sur…") },
                )
            }

            if (etat.rythme is Rythme.UnJourSurN) {
                OutlinedTextField(
                    value = etat.intervalleJours,
                    onValueChange = vue::modifierIntervalle,
                    label = { Text("Un jour sur") },
                    suffix = { Text("jours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(180.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { jour ->
                    val actifs = (etat.rythme as? Rythme.JoursDeSemaine)?.jours ?: emptySet()
                    FilterChip(
                        selected = jour in actifs,
                        onClick = { vue.basculerJourDeSemaine(jour) },
                        label = { Text(libelleJour(jour)) },
                    )
                }
            }

            // Reserve a la creation : c'est la qu'elle fixe le debut du traitement
            // et l'ancrage du rythme. Sur une modification, enregistrerVersion
            // ecrase dateDebut par la date d'effet — le champ acceptait donc une
            // saisie qu'il jetait en silence. « S'applique a partir du » est le
            // seul controle de date qui agit alors.
            if (etat.id == 0L) {
                SelecteurDate(
                    libelle = "Début du traitement",
                    date = etat.dateDebut,
                    surChangement = vue::modifierDateDebut,
                )
            }
            SelecteurDateOptionnelle(
                libelle = "Fin du traitement",
                date = etat.dateFin,
                dateDebut = etat.dateDebut,
                surChangement = vue::modifierDateFin,
            )

            // Visible en modification seulement : a la creation, « Début du
            // traitement » joue deja ce role et un second champ egarerait.
            if (etat.id != 0L) {
                SelecteurDate(
                    libelle = "S'applique à partir du",
                    date = etat.dateEffet,
                    surChangement = vue::modifierDateEffet,
                )
            }

            Text("Doses par moment", style = MaterialTheme.typography.titleSmall)
            Moment.entries.forEach { moment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(libelleMoment(moment), modifier = Modifier.padding(end = 8.dp))
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) - 0.5) }) {
                        Text("−")
                    }
                    Text(
                        formaterDose(etat.doses[moment] ?: 0.0),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) + 0.5) }) {
                        Text("+")
                    }
                }
            }

            OutlinedTextField(
                value = etat.seuilAlerteJours,
                onValueChange = vue::modifierSeuilJours,
                label = { Text("Alerter X jours avant la fin (vide = défaut)") },
            )
        } else {
            OutlinedTextField(
                value = etat.seuilAlerteUnites,
                onValueChange = vue::modifierSeuilUnites,
                label = { Text("Alerter sous X unités restantes") },
            )
        }

        etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vue.enregistrer() }) { Text("Enregistrer") }
            if (etat.id != 0L) {
                OutlinedButton(onClick = { confirmationSuppression = true }) { Text("Archiver") }
            }
        }
    }
}

private val formatDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelecteurDate(
    libelle: String,
    date: LocalDate,
    surChangement: (LocalDate) -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(libelle, modifier = Modifier.padding(end = 8.dp))
        OutlinedButton(onClick = { ouvert = true }) { Text(date.format(formatDate)) }
    }

    if (ouvert) {
        val etatSelecteur = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * MILLIS_PAR_JOUR,
        )

        DatePickerDialog(
            onDismissRequest = { ouvert = false },
            confirmButton = {
                Button(
                    onClick = {
                        etatSelecteur.selectedDateMillis?.let {
                            surChangement(LocalDate.ofEpochDay(it / MILLIS_PAR_JOUR))
                        }
                        ouvert = false
                    },
                ) {
                    Text("Valider")
                }
            },
        ) {
            DatePicker(state = etatSelecteur)
        }
    }
}

@Composable
private fun SelecteurDateOptionnelle(
    libelle: String,
    date: LocalDate?,
    dateDebut: LocalDate,
    surChangement: (LocalDate?) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = date != null,
                onCheckedChange = { actif ->
                    surChangement(if (actif) dateDebut.plusDays(6) else null)
                },
            )
            Text(
                if (date == null) "$libelle : illimité (traitement au long cours)" else libelle,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Une cure datée : la date de fin n'apparaît que si elle existe.
        date?.let { SelecteurDate(libelle = "Dernier jour", date = it, surChangement = surChangement) }
    }
}
