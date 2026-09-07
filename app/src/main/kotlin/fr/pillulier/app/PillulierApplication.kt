package fr.pillulier.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.PlanificateurQuotidien
import javax.inject.Inject

@HiltAndroidApp
class PillulierApplication : Application(), Configuration.Provider {

    @Inject lateinit var fabriqueTravailleurs: HiltWorkerFactory
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var planificateur: PlanificateurQuotidien

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabriqueTravailleurs).build()

    override fun onCreate() {
        super.onCreate()
        notifications.creerCanaux()
        planificateur.planifier()
    }
}
