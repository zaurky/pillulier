package fr.pillulier.app.ui.medicaments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.ChampsFiche
import fr.pillulier.app.ui.formaterDose
import fr.pillulier.app.ui.libelleJour
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
        // La fiche se saisit a la creation, puis se corrige depuis l'onglet
        // Stock : en modification cet ecran n'edite que le traitement.
        if (etat.id == 0L) {
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
        }

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

            // Sept FilterChip debordaient : « Di » sortait de l'ecran. Sept
            // pastilles de 40 dp tiennent jusqu'a un ecran de 320 dp.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val actifs = (etat.rythme as? Rythme.JoursDeSemaine)?.jours ?: emptySet()
                DayOfWeek.entries.forEach { jour ->
                    PastilleJour(
                        libelle = libelleJour(jour),
                        description = jour.getDisplayName(TextStyle.FULL, Locale.FRENCH),
                        coche = jour in actifs,
                        surClic = { vue.basculerJourDeSemaine(jour) },
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
                // Le moment prend la place libre, les boutons se calent a droite :
                // les quatre lignes s'alignent en colonne quelle que soit la
                // longueur du libelle. La largeur fixe de la dose garde le « + »
                // immobile entre « 0 » et « 1½ ».
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(libelleMoment(moment), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) - 0.5) }) {
                        Text("−")
                    }
                    Text(
                        formaterDose(etat.doses[moment] ?: 0.0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp),
                    )
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) + 0.5) }) {
                        Text("+")
                    }
                }
            }

            if (etat.id == 0L) {
                OutlinedTextField(
                    value = etat.seuilAlerteJours,
                    onValueChange = vue::modifierSeuilJours,
                    label = { Text("Alerter X jours avant la fin (vide = défaut)") },
                )
            }
        } else if (etat.id == 0L) {
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

@Composable
private fun PastilleJour(
    libelle: String,
    description: String,
    coche: Boolean,
    surClic: () -> Unit,
) {
    val couleurs = MaterialTheme.colorScheme
    Surface(
        onClick = surClic,
        shape = CircleShape,
        color = if (coche) couleurs.primary else Color.Transparent,
        contentColor = if (coche) couleurs.onPrimary else couleurs.onSurface,
        border = if (coche) null else BorderStroke(1.dp, couleurs.outline),
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = description
                selected = coche
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(libelle, style = MaterialTheme.typography.labelLarge)
        }
    }
}

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
