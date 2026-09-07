package fr.pillulier.app.rappels

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Alarme des médicaments critiques : par-dessus l'écran verrouillé, avec son. */
@AndroidEntryPoint
class AlarmeActivity : ComponentActivity() {

    private val vue: AlarmeViewModel by viewModels()
    private var sonnerie: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        presenter(intent)

        demarrerSonnerie()

        setContent {
            val etat by vue.etat.collectAsStateWithLifecycle()

            LaunchedEffect(etat.termine) {
                if (etat.termine) finish()
            }

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(etat.momentLisible, style = MaterialTheme.typography.titleMedium)
                        Text(etat.nom, style = MaterialTheme.typography.headlineLarge)
                        Text(etat.dosage, style = MaterialTheme.typography.titleLarge)
                        Text(etat.libelleDose, style = MaterialTheme.typography.headlineSmall)

                        Row(
                            modifier = Modifier.padding(top = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(onClick = { vue.validerDepuisUi() }) { Text("Pris") }
                            OutlinedButton(onClick = { vue.reporterDepuisUi() }) { Text("Plus tard") }
                        }
                    }
                }
            }
        }
    }

    /**
     * L'activité est `singleInstance` : un second médicament critique du même
     * moment — les comprimés puis la piqûre — atteint l'instance vivante sans
     * repasser par `onCreate`. Sans cela, le second rappel resterait muet
     * derrière l'écran du premier.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        presenter(intent)
    }

    private fun presenter(intention: Intent) {
        val cle = cleDepuisIntent(intention)
        val dose = intention.getDoubleExtra(EXTRA_DOSE, 0.0)
        lifecycleScope.launch { vue.charger(cle, dose) }
    }

    private fun demarrerSonnerie() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        sonnerie = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    override fun onDestroy() {
        sonnerie?.stop()
        super.onDestroy()
    }
}
