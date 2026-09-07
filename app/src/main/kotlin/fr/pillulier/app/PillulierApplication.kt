package fr.pillulier.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.pillulier.app.rappels.Notifications
import javax.inject.Inject

@HiltAndroidApp
class PillulierApplication : Application() {

    @Inject lateinit var notifications: Notifications

    override fun onCreate() {
        super.onCreate()
        notifications.creerCanaux()
    }
}
