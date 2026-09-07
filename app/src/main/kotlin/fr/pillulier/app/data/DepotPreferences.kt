package fr.pillulier.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

private val CLE_DELAI_PLUS_TARD = intPreferencesKey("delai_plus_tard_minutes")
private val CLE_INTERVALLE_RELANCE = intPreferencesKey("intervalle_relance_minutes")
private val CLE_SEUIL_ALERTE_JOURS = intPreferencesKey("seuil_alerte_jours_defaut")
private val CLE_AUTORISATIONS_VUES = booleanPreferencesKey("autorisations_vues")

const val DELAI_PLUS_TARD_DEFAUT = 15
const val INTERVALLE_RELANCE_DEFAUT = 15
const val SEUIL_ALERTE_JOURS_DEFAUT = 7

data class PreferencesPillulier(
    val delaiPlusTardMinutes: Int = DELAI_PLUS_TARD_DEFAUT,
    val intervalleRelanceMinutes: Int = INTERVALLE_RELANCE_DEFAUT,
    val seuilAlerteJoursDefaut: Int = SEUIL_ALERTE_JOURS_DEFAUT,
)

@Singleton
class DepotPreferences @Inject constructor(private val contexte: Context) {

    val preferences: Flow<PreferencesPillulier> = contexte.dataStore.data.map { stockees ->
        PreferencesPillulier(
            delaiPlusTardMinutes = stockees[CLE_DELAI_PLUS_TARD] ?: DELAI_PLUS_TARD_DEFAUT,
            intervalleRelanceMinutes = stockees[CLE_INTERVALLE_RELANCE] ?: INTERVALLE_RELANCE_DEFAUT,
            seuilAlerteJoursDefaut = stockees[CLE_SEUIL_ALERTE_JOURS] ?: SEUIL_ALERTE_JOURS_DEFAUT,
        )
    }

    suspend fun instantane(): PreferencesPillulier = preferences.first()

    val autorisationsVues: Flow<Boolean> =
        contexte.dataStore.data.map { it[CLE_AUTORISATIONS_VUES] ?: false }

    suspend fun marquerAutorisationsVues() {
        contexte.dataStore.edit { it[CLE_AUTORISATIONS_VUES] = true }
    }

    suspend fun definirDelaiPlusTard(minutes: Int) =
        ecrire(CLE_DELAI_PLUS_TARD, minutes)

    suspend fun definirIntervalleRelance(minutes: Int) =
        ecrire(CLE_INTERVALLE_RELANCE, minutes)

    suspend fun definirSeuilAlerteJours(jours: Int) =
        ecrire(CLE_SEUIL_ALERTE_JOURS, jours)

    private suspend fun ecrire(cle: Preferences.Key<Int>, valeur: Int) {
        require(valeur > 0) { "une duree ou un seuil doit etre strictement positif, recu $valeur" }
        contexte.dataStore.edit { it[cle] = valeur }
    }
}
