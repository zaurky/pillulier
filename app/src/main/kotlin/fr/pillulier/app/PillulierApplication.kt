package fr.pillulier.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.pillulier.app.debug.JournalDebug // JOURNAL-DEBUG
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.PlanificateurQuotidien
import fr.pillulier.app.usecase.ReArmerRappels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PillulierApplication : Application(), Configuration.Provider {

    @Inject lateinit var fabriqueTravailleurs: HiltWorkerFactory
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var planificateur: PlanificateurQuotidien
    @Inject lateinit var reArmerRappels: ReArmerRappels

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabriqueTravailleurs).build()

    override fun onCreate() {
        super.onCreate()
        JournalDebug.initialiser(this) // JOURNAL-DEBUG
        notifications.creerCanaux()
        planificateur.planifier()

        // Android efface les alarmes en attente au remplacement du paquet et à
        // l'arrêt forcé : le lancement de l'application est le seul moment où
        // l'on peut rattraper ces deux cas. Le réarmement touche la base, donc
        // il part sur une portée d'entrées-sorties, jamais sur le fil principal.
        CoroutineScope(Dispatchers.IO).launch { reArmerRappels() }
    }
}
