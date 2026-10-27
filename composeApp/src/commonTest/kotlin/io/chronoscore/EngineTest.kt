package io.chronoscore

import io.chronoscore.engine.GameAction
import io.chronoscore.engine.GameEngine
import io.chronoscore.model.GameSession

/** Shared setup for engine tests: a fixed clock tests can move, and predictable ids. */
abstract class EngineTest {
    protected var now = 1_000_000L
    private var nextId = 0
    protected val engine = GameEngine(clock = { now }, newId = { "id${nextId++}" })

    protected fun GameSession.apply(vararg actions: GameAction) = actions.fold(this) { s, a -> engine.reduce(s, a) }
    protected fun GameSession.id(seat: Int) = players[seat].id
}
