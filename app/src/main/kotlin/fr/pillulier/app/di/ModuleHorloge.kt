package fr.pillulier.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.temps.HorlogeSysteme
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleHorloge {
    @Binds
    @Singleton
    abstract fun horloge(systeme: HorlogeSysteme): Horloge
}
