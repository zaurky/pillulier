package fr.pillulier.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.ui.PillulierNavigation
import fr.pillulier.app.ui.autorisations.AutorisationsEcran
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: DepotPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vues by preferences.autorisationsVues.collectAsState(initial = true)
            val portee = rememberCoroutineScope()

            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    if (vues) {
                        PillulierNavigation()
                    } else {
                        AutorisationsEcran(
                            surTermine = { portee.launch { preferences.marquerAutorisationsVues() } },
                        )
                    }
                }
            }
        }
    }
}
