package fr.pillulier.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import javax.inject.Inject

/**
 * Le flux collecté par le widget porte déjà toute écriture en base. Restent les
 * deux changements qu'aucune écriture ne signale : le passage à un nouveau jour,
 * et une prise qui devient en retard par le seul écoulement du temps.
 */
class RafraichirWidget @Inject constructor(private val contexte: Context) {
    suspend operator fun invoke() = PillulierWidget.updateAll(contexte)
}
