package com.resbox.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import com.resbox.player.data.local.datastore.AppPreferences
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.ui.navigation.ResBoxAdaptiveRoot
import com.resbox.player.ui.theme.ResBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by settingsDataStore.preferencesFlow.collectAsState(initial = AppPreferences())
            ResBoxTheme(
                enableDynamicColor = prefs.enableDynamicColor,
                forceTvMode = prefs.forceTvMode
            ) {
                ResBoxAdaptiveRoot()
            }
        }
    }
}
