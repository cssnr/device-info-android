package org.cssnr.deviceinfo.data

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_HUE = floatPreferencesKey("seed_hue")
        val COPY_KEY_AND_VALUE = booleanPreferencesKey("copy_key_and_value")
        const val CRASH_REPORTING = "acra.enable"
    }

    @Suppress("DEPRECATION")
    private val acraPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    // Dynamic defaults to true so a fresh install follows the wallpaper on Android 12+.
    // Below Android 12 the theme layer falls back to the baseline, so the default is harmless.
    // The static seed is a hue on the vivid ramp; the default is the hue of the Material 3
    // baseline purple, so the slider, preview, and theme all agree on one value.
    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.DYNAMIC_COLOR] ?: true }

    val seedHue: Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.SEED_HUE] ?: DEFAULT_SEED_HUE }

    // Defaults to true, so a tap copies the same `Label: Value` line the copy and share buttons
    // produce. That is the more useful default because a row on its own is usually being pasted
    // somewhere the reader needs to know which field it came from.
    val copyKeyAndValue: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.COPY_KEY_AND_VALUE] ?: true }

    val crashReporting: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Keys.CRASH_REPORTING) {
                trySend(acraPreferences.getBoolean(Keys.CRASH_REPORTING, true))
            }
        }
        acraPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(acraPreferences.getBoolean(Keys.CRASH_REPORTING, true))
        awaitClose { acraPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setSeedHue(hue: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SEED_HUE] = hue
        }
    }

    suspend fun setCopyKeyAndValue(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.COPY_KEY_AND_VALUE] = enabled
        }
    }

    fun setCrashReporting(enabled: Boolean) {
        acraPreferences.edit().putBoolean(Keys.CRASH_REPORTING, enabled).apply()
    }

    companion object {
        // Hue of the Material 3 baseline purple (#6750A4) on the vivid (S=V=1) ramp.
        const val DEFAULT_SEED_HUE = 256.43f
    }
}