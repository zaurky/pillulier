package fr.pillulier.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.pillulier.domain.Moment

@Database(
    entities = [
        MedicamentEntity::class,
        OrdonnanceEntity::class,
        DosePrescriteEntity::class,
        MomentConfigEntity::class,
        EvenementPriseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class PillulierDatabase : RoomDatabase() {
    abstract fun medicaments(): MedicamentDao
    abstract fun ordonnances(): OrdonnanceDao
    abstract fun evenements(): EvenementPriseDao
    abstract fun moments(): MomentConfigDao
}

/** Amorce les quatre moments à leurs heures par défaut, à la création de la base. */
val momentsParDefaut = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        listOf(
            Moment.MATIN to 8 * 60,
            Moment.MIDI to 12 * 60,
            Moment.SOIR to 19 * 60,
            Moment.COUCHER to 22 * 60,
        ).forEach { (moment, minutes) ->
            db.execSQL("INSERT INTO moment_config (moment, heure) VALUES ('${moment.name}', $minutes)")
        }
    }
}
