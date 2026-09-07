package fr.pillulier.app.rappels

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.pillulier.app.usecase.CloturerJournee
import fr.pillulier.app.usecase.ReArmerRappels

/** Clôture la veille puis réarme la fenêtre de trois jours. */
@HiltWorker
class TravailQuotidien @AssistedInject constructor(
    @Assisted contexte: Context,
    @Assisted parametres: WorkerParameters,
    private val cloturerJournee: CloturerJournee,
    private val reArmerRappels: ReArmerRappels,
) : CoroutineWorker(contexte, parametres) {

    override suspend fun doWork(): Result {
        cloturerJournee()
        reArmerRappels()
        return Result.success()
    }

    companion object {
        const val NOM = "travail-quotidien"
    }
}
