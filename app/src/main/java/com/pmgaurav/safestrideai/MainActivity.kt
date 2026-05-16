package com.pmgaurav.safestrideai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pmgaurav.safestrideai.ui.MainViewModel
import com.pmgaurav.safestrideai.ui.AppNavigation
import com.pmgaurav.safestrideai.ui.theme.SafeStrideAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        SafeStrideSDK.getInstance(this)

        setContent {
            SafeStrideAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.arOverlayManager.onSessionResumed()
    }

    override fun onPause() {
        viewModel.arOverlayManager.onSessionPaused()
        super.onPause()
    }
}

