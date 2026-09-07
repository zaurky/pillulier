package fr.pillulier.app.ui.autorisations

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Les quatre autorisations, dans l'ordre. Sans l'exemption d'optimisation de
 * batterie, aucune app de rappel de médicaments n'est fiable sur les surcouches
 * constructeur : c'est pour cela qu'elle est présentée ici et pas cachée.
 */
@Composable
fun AutorisationsEcran(surTermine: () -> Unit) {
    val contexte = LocalContext.current

    val demandeNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        // Hors d'un `Scaffold`, personne ne consomme les encarts système : sans
        // ce rembourrage le titre passerait sous la barre d'état, et c'est le
        // premier écran que voit un nouvel utilisateur.
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Autorisations", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pour que les rappels arrivent à l'heure, l'application a besoin de " +
                "quatre autorisations.",
        )

        Button(
            onClick = { demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("1. Autoriser les notifications")
        }

        Button(
            onClick = { contexte.ouvrirAlarmesExactes() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("2. Autoriser les alarmes exactes")
        }

        Button(
            onClick = { contexte.ouvrirPleinEcran() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("3. Autoriser les alarmes plein écran")
        }

        Button(
            onClick = { contexte.ouvrirOptimisationBatterie() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("4. Désactiver l'optimisation de batterie")
        }

        Button(onClick = surTermine, modifier = Modifier.fillMaxWidth()) {
            Text("Terminé")
        }
    }
}

private fun Context.ouvrirAlarmesExactes() {
    val gestionnaire = getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !gestionnaire.canScheduleExactAlarms()) {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}

private fun Context.ouvrirPleinEcran() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

private fun Context.ouvrirOptimisationBatterie() {
    val gestionnaire = getSystemService(PowerManager::class.java)
    if (!gestionnaire.isIgnoringBatteryOptimizations(packageName)) {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}
