package com.vigo.driverlog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vigo.driverlog.domain.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val driverName: String = "",
    val vehiclePlate: String = "",
    val companyName: String = "",
    val use24h: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val onlineMapInReport: Boolean = true,
) {
    val isProfileComplete get() = driverName.isNotBlank() && vehiclePlate.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private object K {
        val driver = stringPreferencesKey("driver_name")
        val plate = stringPreferencesKey("vehicle_plate")
        val company = stringPreferencesKey("company_name")
        val use24h = booleanPreferencesKey("use_24h")
        val unit = stringPreferencesKey("distance_unit")
        val onlineMap = booleanPreferencesKey("online_map_in_report")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                driverName = p[K.driver] ?: "",
                vehiclePlate = p[K.plate] ?: "",
                companyName = p[K.company] ?: "",
                use24h = p[K.use24h] ?: true,
                distanceUnit = DistanceUnit.from(p[K.unit]),
                onlineMapInReport = p[K.onlineMap] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveProfile(driver: String, plate: String, company: String) {
        context.dataStore.edit {
            it[K.driver] = driver.trim()
            it[K.plate] = plate.trim().uppercase()
            it[K.company] = company.trim()
        }
    }

    suspend fun setUse24h(v: Boolean) { context.dataStore.edit { it[K.use24h] = v } }
    suspend fun setDistanceUnit(v: DistanceUnit) { context.dataStore.edit { it[K.unit] = v.name } }
    suspend fun setOnlineMap(v: Boolean) { context.dataStore.edit { it[K.onlineMap] = v } }
}
