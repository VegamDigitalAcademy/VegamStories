package com.vegam.myapplication.data.local

import androidx.room.TypeConverter
import com.vegam.myapplication.data.local.entity.PanelLocal
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun panelsToJson(panels: List<PanelLocal>): String = json.encodeToString(panels)

    @TypeConverter
    fun jsonToPanels(value: String): List<PanelLocal> = json.decodeFromString(value)
}
