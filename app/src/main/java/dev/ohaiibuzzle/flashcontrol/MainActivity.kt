package dev.ohaiibuzzle.flashcontrol

import android.os.Bundle
import androidx.activity.compose.setContent
import dev.ohaiibuzzle.flashcontrol.ui.FlashControlApp
import dev.ohaiibuzzle.flashcontrol.ui.theme.FlashControlTheme

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlashControlTheme {
                FlashControlApp()
            }
        }
    }
}
