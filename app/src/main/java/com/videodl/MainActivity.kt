package com.videodl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.videodl.ui.VideoDLApp
import com.videodl.ui.VideoDLTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoDLTheme {
                VideoDLApp()
            }
        }
    }
}
