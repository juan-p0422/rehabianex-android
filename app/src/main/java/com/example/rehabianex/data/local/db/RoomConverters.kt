package com.example.rehabianex.data.local.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    private val gson = Gson()

    @TypeConverter
    fun stringListToJson(value: List<String>?): String = gson.toJson(value.orEmpty())

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(
                value,
                object : TypeToken<List<String>>() {}.type,
            ).orEmpty()
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun noteSyncStateToString(value: PatientNoteSyncState): String = value.persistedValue

    @TypeConverter
    fun stringToNoteSyncState(value: String?): PatientNoteSyncState {
        return PatientNoteSyncState.entries.firstOrNull { it.persistedValue == value }
            ?: PatientNoteSyncState.Synced
    }
}
