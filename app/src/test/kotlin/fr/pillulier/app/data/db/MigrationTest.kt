package fr.pillulier.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private const val BASE = "migration-test"

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val aide = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PillulierDatabase::class.java,
    )

    @Test
    fun `la migration 1 vers 2 ancre le rythme sur la date de debut`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                    "rythmeN, dateDebut, dateFin) " +
                    "VALUES (1, 1, 'PLANIFIEE', 'UN_JOUR_SUR_N', NULL, 2, 20000, NULL)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        migree.query("SELECT dateAncrage, dateDebut FROM ordonnance WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(20000L, it.getLong(0))
            assertEquals(it.getLong(1), it.getLong(0))
        }
    }

    @Test
    fun `la migration 1 vers 2 conserve les prises enregistrees`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO evenement_prise (id, medicamentId, date, moment, doseReelle, enregistreLe) " +
                    "VALUES (1, 1, 20000, 'MATIN', 1.0, 1700000000000)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        migree.query("SELECT COUNT(*) FROM evenement_prise").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    /**
     * `dose_prescrite` est la seule table que la migration ne recree pas alors
     * qu elle detruit son parent : `DROP TABLE ordonnance` effacerait ses
     * lignes en cascade si les cles etrangeres etaient actives. Room ne les
     * active qu au `onOpen`, donc apres les migrations — ce test epingle cette
     * dependance implicite, qui ne tient a aucune ligne de notre code.
     */
    @Test
    fun `la migration 1 vers 2 conserve les doses prescrites`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                    "rythmeN, dateDebut, dateFin) " +
                    "VALUES (7, 1, 'PLANIFIEE', 'TOUS_LES_JOURS', NULL, NULL, 20000, NULL)",
            )
            base.execSQL(
                "INSERT INTO dose_prescrite (id, ordonnanceId, moment, dose) " +
                    "VALUES (1, 7, 'MATIN', 1.5)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        // La jointure verifie les deux moities du risque d un coup : la ligne
        // doit survivre, et son `ordonnanceId` doit encore designer l ordonnance
        // recreee — un identifiant reattribue au passage la laisserait pendante.
        migree.query(
            "SELECT d.ordonnanceId, d.moment, d.dose FROM dose_prescrite d " +
                "JOIN ordonnance o ON o.id = d.ordonnanceId WHERE d.id = 1",
        ).use {
            assertEquals(true, it.moveToFirst(), "la dose prescrite doit survivre, rattachee a son ordonnance")
            assertEquals(7L, it.getLong(0))
            assertEquals("MATIN", it.getString(1))
            assertEquals(1.5, it.getDouble(2))
        }
    }

    @Test
    fun `apres migration un medicament porte plusieurs ordonnances`() {
        aide.createDatabase(BASE, 1).use { base ->
            base.execSQL(
                "INSERT INTO medicament (id, nom, dosage, forme, unitesParBoite, stockUnites, " +
                    "seuilAlerteJours, seuilAlerteUnites, critique) " +
                    "VALUES (1, 'Levothyrox', '75', 'COMPRIME', 30, 30.0, 7, NULL, 0)",
            )
            base.execSQL(
                "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                    "rythmeN, dateDebut, dateFin) " +
                    "VALUES (1, 1, 'PLANIFIEE', 'TOUS_LES_JOURS', NULL, NULL, 20000, 20009)",
            )
        }

        val migree = aide.runMigrationsAndValidate(BASE, 2, true, MIGRATION_1_2)

        // L'index unique a disparu : une seconde version doit passer.
        migree.execSQL(
            "INSERT INTO ordonnance (id, medicamentId, type, rythmeType, rythmeJours, " +
                "rythmeN, dateDebut, dateFin, dateAncrage) " +
                "VALUES (2, 1, 'PLANIFIEE', 'TOUS_LES_JOURS', NULL, NULL, 20010, NULL, 20000)",
        )

        migree.query("SELECT COUNT(*) FROM ordonnance WHERE medicamentId = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
    }
}
