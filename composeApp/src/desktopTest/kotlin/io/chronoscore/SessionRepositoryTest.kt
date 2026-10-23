package io.chronoscore

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.chronoscore.data.SessionRepository
import io.chronoscore.data.openDesktopDriver
import io.chronoscore.db.ChronoscoreDatabase
import io.chronoscore.engine.BulkMode
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.data.BackupCodec
import io.chronoscore.model.CounterTarget
import io.chronoscore.model.DayNight
import io.chronoscore.model.GamePreset
import io.chronoscore.model.GameSession
import io.chronoscore.model.MatchOptions
import io.chronoscore.model.Profile
import io.chronoscore.model.TimeBonus
import io.chronoscore.model.TimeControl
import io.chronoscore.model.WinMode
import io.chronoscore.model.WinRule
import io.chronoscore.viewmodel.GameViewModel
import io.chronoscore.viewmodel.SeatStyle
import io.chronoscore.viewmodel.ThemeMode
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.sql.DriverManager
import java.util.Properties
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRepositoryTest {

    private var clock = 1L
    private val repo = SessionRepository(
        JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") },
            ChronoscoreDatabase.Schema,
        ),
        clock = { clock++ },
    )
    private val engine = GameEngine()

    @AfterTest
    fun close() = repo.close()

    private fun sampleSession(game: String? = "Commander") = engine.newSession(3, 40, game).let { s ->
        val p = s.players[1].id
        val withCounter = engine.reduce(s, GameAction.AddSubCounter(p, "Poison"))
        val sub = withCounter.players[1].subCounters.single().id
        listOf(
            GameAction.Adjust(p, CounterTarget.Primary, -7),
            GameAction.Adjust(p, CounterTarget.Sub(sub), 2),
            GameAction.RotateSeat(s.players[0].id, 180),
            GameAction.RotateSeat(s.players[1].id, 30),
            GameAction.NextRound,
            GameAction.SetValue(s.players[2].id, CounterTarget.Primary, 12),
            GameAction.BulkEdit(s.players.map { it.id }, null, BulkMode.ADD, 1),
        ).fold(withCounter, engine::reduce)
    }

    private fun tempDbFile() = File.createTempFile("chronoscore", ".db").apply { delete(); deleteOnExit() }

    @Test
    fun emptyDatabaseLoadsNothing() {
        assertNull(repo.loadLatest())
        assertEquals(emptyList(), repo.summaries())
    }

    @Test
    fun saveLoadRoundTripKeepsGameAndBatches() {
        val session = sampleSession()
        repo.save(session)
        assertEquals(session, repo.loadLatest())
        assertEquals(session, repo.load(session.id))
    }

    @Test
    fun resaveReplacesRows() {
        val session = sampleSession()
        repo.save(session)
        val updated = engine.reduce(session, GameAction.Undo)
        repo.save(updated)
        assertEquals(updated, repo.loadLatest())
    }

    @Test
    fun everyMatchIsKeptAndSummarizedMostRecentFirst() {
        val catan = sampleSession("Catan")
        val quick = engine.newSession(2, 20)
        repo.save(catan)
        repo.save(quick)

        val summaries = repo.summaries()
        assertEquals(listOf(quick.id, catan.id), summaries.map { it.id })
        val c = summaries.last()
        assertEquals("Catan", c.gameName)
        assertEquals(catan.players.map { it.name }, c.playerNames)
        assertEquals(catan.historyLog.size, c.entryCount)
        assertEquals(2, c.round)
        assertNull(summaries.first().gameName)

        // Saving an older match again makes it the latest (it was continued).
        repo.save(catan)
        assertEquals(catan, repo.loadLatest())
    }

    @Test
    fun deleteCascadesToChildRows() {
        val session = sampleSession()
        repo.save(session)
        assertEquals(3L + 1L + session.historyLog.size, repo.childRowCount(session.id))
        repo.delete(session.id)
        assertEquals(0L, repo.childRowCount(session.id))
        assertNull(repo.loadLatest())
    }

    @Test
    fun deleteGameRemovesEveryMatchOfThatGame() {
        val catan = sampleSession("Catan")
        val catanLower = engine.newSession(2, 10, "catan")
        val commander = sampleSession()
        val unnamed = engine.newSession(2, 20)
        listOf(catan, catanLower, commander, unnamed).forEach(repo::save)

        assertEquals(setOf(catan.id, catanLower.id), repo.deleteGame("CATAN").toSet())
        assertEquals(setOf(commander.id, unnamed.id), repo.summaries().map { it.id }.toSet())
        assertEquals(0L, repo.childRowCount(catan.id))
        assertEquals(0L, repo.childRowCount(catanLower.id))
        assertEquals(emptyList(), repo.deleteGame("Catan"))
    }

    @Test
    fun settingsPersist() {
        assertNull(repo.setting("theme"))
        repo.putSetting("theme", "DARK")
        repo.putSetting("theme", "LIGHT")
        assertEquals("LIGHT", repo.setting("theme"))
    }

    @Test
    fun fileDatabaseSurvivesReopen() {
        val file = tempDbFile()
        val session = sampleSession()
        SessionRepository(openDesktopDriver(file)).use { it.save(session) }
        SessionRepository(openDesktopDriver(file)).use { assertEquals(session, it.loadLatest()) }
    }

    @Test
    fun migratesVersion1Database() {
        val file = tempDbFile()
        // The original v1 schema with one saved match.
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                listOf(
                    "CREATE TABLE session (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, starting_score INTEGER NOT NULL, round INTEGER NOT NULL, timestamp INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                    "CREATE TABLE player (id TEXT NOT NULL, session_id TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE, seat_index INTEGER NOT NULL, name TEXT NOT NULL, color_hex TEXT NOT NULL, primary_score INTEGER NOT NULL, is_flipped INTEGER NOT NULL, PRIMARY KEY (session_id, id))",
                    "CREATE TABLE sub_counter (id TEXT NOT NULL, session_id TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE, player_id TEXT NOT NULL, position INTEGER NOT NULL, name TEXT NOT NULL, counter_value INTEGER NOT NULL, PRIMARY KEY (session_id, player_id, id))",
                    "CREATE TABLE history_entry (id TEXT NOT NULL, session_id TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE, position INTEGER NOT NULL, timestamp INTEGER NOT NULL, round INTEGER NOT NULL, player_id TEXT NOT NULL, sub_counter_id TEXT, delta INTEGER NOT NULL, resulting_value INTEGER NOT NULL, PRIMARY KEY (session_id, id))",
                    "CREATE INDEX history_entry_session ON history_entry(session_id, position)",
                    "INSERT INTO session VALUES ('s1', 'Match', 20, 2, 100, 200)",
                    "INSERT INTO player VALUES ('p1', 's1', 0, 'Mara', '#2563EB', 17, 0)",
                    "INSERT INTO history_entry VALUES ('h1', 's1', 0, 150, 1, 'p1', NULL, -3, 17)",
                    "PRAGMA user_version = 1",
                ).forEach(st::execute)
            }
        }

        SessionRepository(openDesktopDriver(file)).use { migrated ->
            val s = migrated.loadLatest()!!
            assertEquals("Mara", s.players.single().name)
            assertEquals(17, s.players.single().primaryScore)
            assertNull(s.gameName)
            assertNull(s.historyLog.single().batchId)

            migrated.save(engine.reduce(s, GameAction.SetGameName("Catan")))
            migrated.putSetting("seat_style", "COLORED")
            assertEquals("Catan", migrated.loadLatest()!!.gameName)
        }
    }

    @Test
    fun matchFeaturesRoundTrip() {
        val options = MatchOptions(
            turnTracking = true, matchClock = true, seatTimers = true, turnLimitSeconds = 90,
            winRule = WinRule(WinMode.ELIMINATION, 0), mtgExtras = true,
        )
        val s = engine.newSession(3, 40, "Commander", options).let { s ->
            listOf(
                GameAction.Adjust(s.players[0].id, CounterTarget.Commander(s.players[1].id), 6),
                GameAction.NextTurn,
                GameAction.SetConceded(s.players[2].id, true),
                GameAction.SetProfile(s.players[1].id, Profile("pr", "Jonas", "#16A34A")),
                GameAction.SetMonarch(s.players[1].id),
                GameAction.SetInitiative(s.players[0].id),
                GameAction.SetDayNight(DayNight.NIGHT),
                GameAction.ToggleClock,
            ).fold(s, engine::reduce)
        }
        repo.save(s)
        assertEquals(s, repo.load(s.id))
    }

    @Test
    fun timerModeRoundTrips() {
        val options = MatchOptions(
            turnTracking = true,
            timeControl = TimeControl(bankSeconds = 300, bonus = TimeBonus.DELAY, bonusSeconds = 5, outOnTimeout = false),
        )
        val s = engine.newSession(2, 0, options = options).let { s ->
            listOf(GameAction.ToggleClock, GameAction.AdjustClock(s.players[1].id, -90_000), GameAction.NextTurn).fold(s, engine::reduce)
        }
        repo.save(s)
        assertEquals(s, repo.load(s.id))
        assertEquals(210_000L, repo.load(s.id)!!.players[1].clockMillis)
    }

    @Test
    fun presetsAndProfilesPersist() {
        val preset = GamePreset("p1", "Hearts", "Hearts", 4, 0, emptyList(), MatchOptions(sheetMode = true))
        repo.savePreset(preset)
        repo.savePreset(preset.copy(name = "Hearts (4p)"))
        assertEquals(listOf(preset.copy(name = "Hearts (4p)")), repo.presets())
        repo.deletePreset("p1")
        assertEquals(emptyList(), repo.presets())

        repo.saveProfile(Profile("b", "Zed", "#2563EB"))
        repo.saveProfile(Profile("a", "ana", "#E11D48"))
        assertEquals(listOf("ana", "Zed"), repo.profiles().map { it.name })
        repo.deleteProfile("a")
        assertEquals(listOf("Zed"), repo.profiles().map { it.name })
    }

    @Test
    fun backupExportsAndImportsIntoAnotherDatabase() {
        val a = sampleSession("Catan")
        val b = engine.newSession(2, 20, options = MatchOptions(turnTracking = true))
        repo.save(a)
        repo.save(b)
        repo.saveProfile(Profile("pr", "Mara", "#2563EB"))
        val text = BackupCodec.encode(repo.exportBackup())

        SessionRepository(openDesktopDriver(tempDbFile())).use { other ->
            val current = engine.newSession(1, 20)
            other.save(current)
            val backup = BackupCodec.decode(text)
            assertEquals(2, other.importBackup(backup, keepSessionId = current.id))
            assertEquals(a, other.load(a.id))
            assertEquals(b, other.load(b.id))
            assertEquals(listOf("Mara"), other.profiles().map { it.name })
            assertEquals(current, other.loadLatest(), "the match on the table stays the latest")
            // Importing again replaces rather than duplicates.
            other.importBackup(backup, keepSessionId = current.id)
            assertEquals(3, other.summaries().size)
        }
    }

    @Test
    fun migratesVersion2DatabaseWithoutExtras() {
        val file = tempDbFile()
        val session = sampleSession()
        SessionRepository(openDesktopDriver(file)).use { it.save(session) }
        // Simulate a 1.0 database: same rows, no JSON columns filled, user_version 2.
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                listOf(
                    "DROP TABLE preset", "DROP TABLE profile",
                    "ALTER TABLE session DROP COLUMN state_json", "ALTER TABLE player DROP COLUMN extras_json",
                    "PRAGMA user_version = 2",
                ).forEach(st::execute)
            }
        }
        SessionRepository(openDesktopDriver(file)).use { migrated ->
            // 1.0 only stored the 180° flip, so the seat turned 30° comes back upright.
            val legacy = session.copy(players = session.players.map { if (it.rotation == 180) it else it.copy(rotation = 0) })
            assertEquals(legacy, migrated.loadLatest())
            migrated.saveProfile(Profile("p", "Mara", "#2563EB"))
            assertEquals(1, migrated.profiles().size)
        }
    }

    @Test
    fun viewModelKeepsEachMatchContinuable() {
        val file = tempDbFile()
        val vm = GameViewModel(SessionRepository(openDesktopDriver(file)), Dispatchers.Unconfined)
        val first = vm.session.value
        val mara = first.players[0].id
        vm.dispatch(GameAction.SetGameName("Catan"))
        vm.dispatch(GameAction.Adjust(mara, CounterTarget.Primary, 5))

        // Starting a new game must persist the outgoing match before switching.
        vm.dispatch(GameAction.NewGame(2, 40, "Commander"))
        val second = vm.session.value
        vm.dispatch(GameAction.Adjust(second.players[1].id, CounterTarget.Primary, -3))
        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setSeatStyle(SeatStyle.COLORED)

        vm.openSession(first.id)
        assertEquals(first.id, vm.session.value.id)
        assertEquals(25, vm.session.value.players[0].primaryScore)
        assertEquals("Catan", vm.session.value.gameName)
        assertEquals(1, vm.session.value.historyLog.size)

        // Continue the old match, then come back to the new one.
        vm.dispatch(GameAction.Adjust(mara, CounterTarget.Primary, 1))
        vm.openSession(second.id)
        assertEquals(37, vm.session.value.players[1].primaryScore)
        vm.flush()

        SessionRepository(openDesktopDriver(file)).use { repo ->
            assertEquals(26, repo.load(first.id)!!.players[0].primaryScore)
            assertEquals(setOf("Catan", "Commander"), repo.summaries().mapNotNull { it.gameName }.toSet())
            assertEquals("LIGHT", repo.setting("theme"))
            assertEquals("COLORED", repo.setting("seat_style"))
        }
    }

    @Test
    fun viewModelDeletingTheCurrentGameMovesTheTable() {
        val file = tempDbFile()
        lateinit var commander: GameSession
        lateinit var catan: GameSession
        lateinit var fresh: GameSession
        // On the UI thread, like the app: the view model's coroutines then can't interleave with the deletes.
        SwingUtilities.invokeAndWait {
            val vm = GameViewModel(SessionRepository(openDesktopDriver(file)), Dispatchers.Unconfined)
            vm.dispatch(GameAction.SetGameName("Commander"))
            commander = vm.session.value
            vm.dispatch(GameAction.NewGame(2, 10, "Catan"))
            catan = vm.session.value

            // The latest remaining match comes back to the table.
            vm.deleteGame("Catan")
            assertEquals(commander.id, vm.session.value.id)

            // With nothing left, a fresh match starts.
            vm.deleteGame("commander")
            fresh = vm.session.value
            assertNull(fresh.gameName)
            vm.flush()
        }

        SessionRepository(openDesktopDriver(file)).use { repo ->
            assertEquals(listOf(fresh.id), repo.summaries().map { it.id })
            assertNull(repo.load(catan.id))
            assertNull(repo.load(commander.id))
        }
    }
}
