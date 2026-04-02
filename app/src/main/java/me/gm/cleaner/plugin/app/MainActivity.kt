package me.gm.cleaner.plugin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.gm.cleaner.plugin.ui.main.MainScreen
import me.gm.cleaner.plugin.ui.theme.MediaProviderManagerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaProviderManagerTheme {
                MainScreen()
            }
        }
    }
}
