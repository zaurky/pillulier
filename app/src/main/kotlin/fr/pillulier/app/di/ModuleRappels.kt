package fr.pillulier.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.rappels.ProgrammateurAlarmesAndroid
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleRappels {
    @Binds
    @Singleton
    abstract fun programmateur(android: ProgrammateurAlarmesAndroid): ProgrammateurAlarmes
}
