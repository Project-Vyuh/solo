package dev.projectvyuh.solo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.projectvyuh.solo.presentation.shell.SoloRoot
import dev.projectvyuh.solo.presentation.theme.SoloTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoloTheme {
                SoloRoot()
            }
        }
    }
}
