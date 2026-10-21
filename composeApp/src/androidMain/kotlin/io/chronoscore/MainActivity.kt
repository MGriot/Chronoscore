package io.chronoscore

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.chronoscore.data.SessionRepository
import io.chronoscore.db.ChronoscoreDatabase
import io.chronoscore.ui.AndroidFileBridge
import io.chronoscore.ui.App
import io.chronoscore.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        viewModelFactory {
            initializer {
                val driver = AndroidSqliteDriver(
                    schema = ChronoscoreDatabase.Schema,
                    context = applicationContext,
                    name = "chronoscore.db",
                    callback = object : AndroidSqliteDriver.Callback(ChronoscoreDatabase.Schema) {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.setForeignKeyConstraintsEnabled(true)
                        }
                    },
                )
                // One writer at a time: autosave and library refreshes share the connection.
                GameViewModel(SessionRepository(driver), Dispatchers.IO.limitedParallelism(1))
            }
        }
    }

    // File pickers must be registered before the activity starts.
    private val files = AndroidFileBridge(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The device sits on the table for the whole match.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App(viewModel, files) }
    }

    override fun onStop() {
        // Leaving the app (or a file picker covering it) pauses the clock; resume from the turn bar.
        if (!isChangingConfigurations) viewModel.pauseClock()
        viewModel.flush()
        super.onStop()
    }
}
