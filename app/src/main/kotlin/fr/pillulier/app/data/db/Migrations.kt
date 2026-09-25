package fr.pillulier.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Premiere migration du projet. SQLite ne sait ni retirer un index unique ni
 * changer une cle etrangere en place : les deux tables concernees sont
 * recreees puis recopiees.
 *
 * `dateAncrage` reprend `dateDebut` : l'ordonnance existante devient la version
 * qui couvre tout le passe, et son rythme garde son origine.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicament ADD COLUMN archiveLe INTEGER DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE ordonnance_nouvelle (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                medicamentId INTEGER NOT NULL,
                type TEXT NOT NULL,
                rythmeType TEXT NOT NULL,
                rythmeJours TEXT,
                rythmeN INTEGER,
                dateDebut INTEGER NOT NULL,
                dateFin INTEGER,
                dateAncrage INTEGER NOT NULL,
                FOREIGN KEY (medicamentId) REFERENCES medicament(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO ordonnance_nouvelle
                (id, medicamentId, type, rythmeType, rythmeJours, rythmeN, dateDebut, dateFin, dateAncrage)
            SELECT id, medicamentId, type, rythmeType, rythmeJours, rythmeN, dateDebut, dateFin, dateDebut
            FROM ordonnance
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE ordonnance")
        db.execSQL("ALTER TABLE ordonnance_nouvelle RENAME TO ordonnance")
        db.execSQL("CREATE INDEX index_ordonnance_medicamentId_dateDebut ON ordonnance(medicamentId, dateDebut)")

        db.execSQL(
            """
            CREATE TABLE evenement_prise_nouveau (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                medicamentId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                moment TEXT,
                doseReelle REAL NOT NULL,
                enregistreLe INTEGER NOT NULL,
                FOREIGN KEY (medicamentId) REFERENCES medicament(id) ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO evenement_prise_nouveau
                (id, medicamentId, date, moment, doseReelle, enregistreLe)
            SELECT id, medicamentId, date, moment, doseReelle, enregistreLe
            FROM evenement_prise
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE evenement_prise")
        db.execSQL("ALTER TABLE evenement_prise_nouveau RENAME TO evenement_prise")
        db.execSQL(
            "CREATE UNIQUE INDEX index_evenement_prise_medicamentId_date_moment " +
                "ON evenement_prise(medicamentId, date, moment)",
        )
        db.execSQL("CREATE INDEX index_evenement_prise_date ON evenement_prise(date)")
    }
}
