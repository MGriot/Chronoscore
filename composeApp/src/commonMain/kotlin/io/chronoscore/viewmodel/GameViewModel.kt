package io.chronoscore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.chronoscore.data.SessionRepository
import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.model.GameSession
import io.chronoscore.model.SessionSummary
import io.chronoscore.random.CoinSide
import io.chronoscore.random.TableRng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UtilityResult {
    data class Dice(val sides: Int, val values: List<Int>) : UtilityResult {
        val total get() = values.sum()
    }
    data class Coin(val side: CoinSide) : UtilityResult
    data class FirstPlayer(val playerName: String) : UtilityResult
    data class TurnOrder(val playerNames: List<String>) : UtilityResult
}

data class UtilityState(
    val last: UtilityResult? = null,
    /** Most recent first; in-memory only, separate from the score history. */
    val log: List<UtilityResult> = emptyList(),
    val highlightedPlayerId: String? = null,
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Neutral: zinc seats with an accent bar. Colored: each seat filled with its accent. */
enum class SeatStyle { NEUTRAL, COLORED }

@OptIn(FlowPreview::class)
class GameViewModel(
    private val repository: SessionRepository?,
    private val ioDispatcher: CoroutineDispatcher,
    private val engine: GameEngine = GameEngine(),
    private val rng: TableRng = TableRng(),
) : ViewModel() {

    private val _session = MutableStateFlow(repository?.loadLatest() ?: engine.newSession())
    val session: StateFlow<GameSession> = _session.asStateFlow()

    private val _utility = MutableStateFlow(UtilityState())
    val utility: StateFlow<UtilityState> = _utility.asStateFlow()

    private val _themeMode = MutableStateFlow(readSetting(KEY_THEME, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _seatStyle = MutableStateFlow(readSetting(KEY_SEAT_STYLE, SeatStyle.NEUTRAL))
    val seatStyle: StateFlow<SeatStyle> = _seatStyle.asStateFlow()

    /** Saved matches, most recently played first. Refresh with [refreshLibrary]. */
    private val _library = MutableStateFlow<List<SessionSummary>>(emptyList())
    val library: StateFlow<List<SessionSummary>> = _library.asStateFlow()

    private var highlightJob: Job? = null

    init {
        if (repository != null) {
            viewModelScope.launch {
                _session.drop(1).debounce(AUTOSAVE_DEBOUNCE_MS).collect { snapshot ->
                    withContext(ioDispatcher) { repository.save(snapshot) }
                }
            }
        }
        refreshLibrary()
    }

    fun dispatch(action: GameAction) {
        // The debounced autosave only sees the latest value, so persist the outgoing match first.
        if (action is GameAction.NewGame) flush()
        _session.update { engine.reduce(it, action) }
        if (action is GameAction.NewGame) refreshLibrary()
    }

    /** Switches to a saved match; it continues with its own players, counters and log. */
    fun openSession(sessionId: String) {
        if (sessionId == _session.value.id) return
        val repo = repository ?: return
        flush()
        repo.load(sessionId)?.let { _session.value = it }
        refreshLibrary()
    }

    fun deleteSession(sessionId: String) {
        if (sessionId == _session.value.id) return
        val repo = repository ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) { repo.delete(sessionId) }
            refreshLibrary()
        }
    }

    fun refreshLibrary() {
        val repo = repository ?: return
        viewModelScope.launch {
            _library.value = withContext(ioDispatcher) {
                // Read the live state here, not at call time: a stale snapshot would overwrite newer edits.
                repo.save(_session.value)
                repo.summaries()
            }
        }
    }

    fun roll(sides: Int, count: Int = 1) = pushUtility(UtilityResult.Dice(sides, rng.rollMany(count, sides)))

    fun flipCoin() = pushUtility(UtilityResult.Coin(rng.flipCoin()))

    fun pickFirstPlayer() {
        val player = rng.pickFirstPlayer(_session.value.players)
        pushUtility(UtilityResult.FirstPlayer(player.name))
        highlightJob?.cancel()
        _utility.update { it.copy(highlightedPlayerId = player.id) }
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_MS)
            _utility.update { it.copy(highlightedPlayerId = null) }
        }
    }

    fun shuffleTurnOrder() =
        pushUtility(UtilityResult.TurnOrder(rng.shuffleTurnOrder(_session.value.players).map { it.name }))

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        repository?.putSetting(KEY_THEME, mode.name)
    }

    fun setSeatStyle(style: SeatStyle) {
        _seatStyle.value = style
        repository?.putSetting(KEY_SEAT_STYLE, style.name)
    }

    /** Synchronously persists the current state; call on app pause / window close. */
    fun flush() {
        repository?.save(_session.value)
    }

    private fun pushUtility(result: UtilityResult) = _utility.update {
        it.copy(last = result, log = (listOf(result) + it.log).take(UTILITY_LOG_SIZE))
    }

    private inline fun <reified E : Enum<E>> readSetting(key: String, default: E): E =
        repository?.setting(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    override fun onCleared() {
        flush()
        repository?.close()
    }

    companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 250L
        const val HIGHLIGHT_MS = 4_000L
        const val UTILITY_LOG_SIZE = 10
        private const val KEY_THEME = "theme"
        private const val KEY_SEAT_STYLE = "seat_style"
    }
}
