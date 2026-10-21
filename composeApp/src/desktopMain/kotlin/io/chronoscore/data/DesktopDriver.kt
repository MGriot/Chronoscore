package io.chronoscore.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.chronoscore.db.ChronoscoreDatabase
import java.io.File
import java.util.Properties

/**
 * Per-user data location: %APPDATA%\Chronoscore on Windows, $XDG_DATA_HOME/chronoscore
 * (default ~/.local/share/chronoscore) on Linux. Override with -Dchronoscore.db=<path>.
 */
fun desktopDatabaseFile(): File {
    System.getProperty("chronoscore.db")?.let { return File(it).also { f -> f.parentFile?.mkdirs() } }
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val dir = when {
        os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "Chronoscore") } ?: File(home, "Chronoscore")
        os.contains("mac") -> File(home, "Library/Application Support/Chronoscore")
        else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "chronoscore")
    }
    dir.mkdirs()
    return File(dir, "chronoscore.db")
}

/** JdbcSqliteDriver creates or migrates the schema via PRAGMA user_version. */
fun openDesktopDriver(file: File = desktopDatabaseFile()): SqlDriver =
    JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        properties = Properties().apply { put("foreign_keys", "true") },
        schema = ChronoscoreDatabase.Schema,
    )
