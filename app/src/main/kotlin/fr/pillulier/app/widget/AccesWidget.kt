package fr.pillulier.app.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.AnnulerPrise
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.ObserverJournee
import fr.pillulier.app.usecase.ReArmerRappels

/**
 * Un `GlanceAppWidget` et ses `ActionCallback` ne sont pas des composants
 * Android : `@AndroidEntryPoint` ne s'y applique pas. On passe donc par un
 * point d'entrée explicite, centralisé ici.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccesWidget {
    fun observerJournee(): ObserverJournee
    fun enregistrerPrise(): EnregistrerPrise
    fun annulerPrise(): AnnulerPrise
    fun reArmerRappels(): ReArmerRappels
    fun notifications(): Notifications
    fun horloge(): Horloge
}

fun acces(contexte: Context): AccesWidget =
    EntryPointAccessors.fromApplication(contexte.applicationContext, AccesWidget::class.java)
