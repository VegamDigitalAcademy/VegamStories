package com.vegam.myapplication.presentation.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Registered in AndroidManifest.xml; the launcher talks to the widget through this. */
class StoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StoryWidget()
}
