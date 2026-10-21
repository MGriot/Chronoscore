package io.chronoscore

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.chronoscore.data.SessionRepository
import io.chronoscore.data.openDesktopDriver
import io.chronoscore.resources.Res
import io.chronoscore.resources.app_icon
import io.chronoscore.ui.App
import io.chronoscore.ui.DesktopFileBridge
import io.chronoscore.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.painterResource

fun main() {
    val viewModel = GameViewModel(SessionRepository(openDesktopDriver()), Dispatchers.IO.limitedParallelism(1))
    application {
        Window(
            onCloseRequest = {
                viewModel.pauseClock()
                viewModel.flush()
                exitApplication()
            },
            title = "Chronoscore",
            icon = painterResource(Res.drawable.app_icon),
            state = rememberWindowState(width = 1100.dp, height = 760.dp),
        ) {
            App(viewModel, remember { DesktopFileBridge(window) })
        }
    }
}
