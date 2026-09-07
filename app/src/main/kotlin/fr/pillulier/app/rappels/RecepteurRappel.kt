package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/** Rempli en tâche 12 : poste la notification et arme la relance. */
@AndroidEntryPoint
class RecepteurRappel : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
