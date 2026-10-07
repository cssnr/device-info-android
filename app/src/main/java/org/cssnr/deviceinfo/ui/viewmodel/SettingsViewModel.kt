package org.cssnr.deviceinfo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.cssnr.deviceinfo.data.SettingsRepository

data class SettingsState(
    val dynamicColor: Boolean = true,
    val seedHue: Float = SettingsRepository.DEFAULT_SEED_HUE,
    val copyKeyAndValue: Boolean = true,
    val crashReporting: Boolean = true,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<SettingsState?> = combine(
        settingsRepository.dynamicColor,
        settingsRepository.seedHue,
        settingsRepository.copyKeyAndValue,
        settingsRepository.crashReporting,
    ) { dynamicColor, seedHue, copyKeyAndValue, crashReporting ->
        SettingsState(
            dynamicColor = dynamicColor,
            seedHue = seedHue,
            copyKeyAndValue = copyKeyAndValue,
            crashReporting = crashReporting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setSeedHue(hue: Float) {
        viewModelScope.launch {
            settingsRepository.setSeedHue(hue)
        }
    }

    fun setCopyKeyAndValue(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCopyKeyAndValue(enabled)
        }
    }

    fun setCrashReporting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReporting(enabled)
        }
    }
}