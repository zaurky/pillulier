package fr.pillulier.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RecepteurWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillulierWidget
}
