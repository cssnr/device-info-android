package org.cssnr.deviceinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cssnr.deviceinfo.data.SettingsRepository
import org.cssnr.deviceinfo.ui.DeviceInfoApp
import org.cssnr.deviceinfo.ui.theme.DeviceInfoTheme

class MainActivity : ComponentActivity() {

    private val settingsViewModel: org.cssnr.deviceinfo.ui.viewmodel.SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            DeviceInfoTheme(
                dynamic = settings?.dynamicColor ?: true,
                seedHue = settings?.seedHue ?: SettingsRepository.DEFAULT_SEED_HUE,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeviceInfoApp()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceInfoAppPreview() {
    DeviceInfoTheme {
        DeviceInfoApp()
    }
}