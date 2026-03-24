package com.aria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aria.data.repository.SecureStorage
import com.aria.ui.navigation.AriaNavHost
import com.aria.ui.navigation.Screen
import com.aria.ui.theme.AriaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (secureStorage.isSetupComplete()) Screen.Home.route else "onboarding"

        setContent {
            AriaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AriaNavHost(startDestination = startDestination)
                }
            }
        }
    }
}
