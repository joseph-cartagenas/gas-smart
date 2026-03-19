package com.smartgas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smartgas.app.navigation.SmartGasNavGraph
import com.smartgas.app.ui.theme.SmartGasTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the SmartGas application.
 *
 * Navigation is handled entirely by [SmartGasNavGraph] using
 * the Compose Navigation component.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartGasTheme {
                SmartGasNavGraph()
            }
        }
    }
}
