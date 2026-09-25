package fr.pillulier.app.rappels

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.pillulier.app.usecase.ClotureQuotidienne

/**
 * Filet du travail de minuit. WorkManager peut differer ce travail de plusieurs
 * heures en Doze — la ponctualite revient a l'alarme exacte de
 * [PlanificateurQuotidien] ; celui-ci garantit seulement qu'il finira par
 * passer si cette alarme se perd.
 */
@HiltWorker
class TravailQuotidien @AssistedInject constructor(
    @Assisted contexte: Context,
    @Assisted parametres: WorkerParameters,
    private val clotureQuotidienne: ClotureQuotidienne,
) : CoroutineWorker(contexte, parametres) {

    override suspend fun doWork(): Result {
        clotureQuotidienne()
        return Result.success()
    }

    companion object {
        const val NOM = "travail-quotidien"
    }
}
