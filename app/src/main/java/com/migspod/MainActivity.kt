package com.migspod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.migspod.ui.MigsPodApp
import com.migspod.ui.theme.MigsPodTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MigsPodTheme {
                MigsPodApp()
            }
        }
    }
}
