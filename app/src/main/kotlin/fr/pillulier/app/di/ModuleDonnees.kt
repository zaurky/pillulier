package fr.pillulier.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.MomentConfigDao
import fr.pillulier.app.data.db.OrdonnanceDao
import fr.pillulier.app.data.db.MIGRATION_1_2
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuleDonnees {

    @Provides
    @Singleton
    fun base(@ApplicationContext contexte: Context): PillulierDatabase =
        Room.databaseBuilder(contexte, PillulierDatabase::class.java, "pillulier.db")
            .addCallback(momentsParDefaut)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun medicamentDao(base: PillulierDatabase): MedicamentDao = base.medicaments()

    @Provides fun ordonnanceDao(base: PillulierDatabase): OrdonnanceDao = base.ordonnances()

    @Provides fun evenementDao(base: PillulierDatabase): EvenementPriseDao = base.evenements()

    @Provides fun momentDao(base: PillulierDatabase): MomentConfigDao = base.moments()

    @Provides
    @Singleton
    fun contexte(@ApplicationContext contexte: Context): Context = contexte
}
