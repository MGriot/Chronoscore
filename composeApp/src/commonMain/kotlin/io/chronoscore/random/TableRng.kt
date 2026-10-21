package io.chronoscore.random

import kotlin.random.Random

enum class CoinSide { HEADS, TAILS }

/** Dice, coin and seat randomizers. Pass a seeded [Random] for deterministic tests. */
class TableRng(private val random: Random = Random.Default) {

    fun roll(sides: Int): Int {
        require(sides in MIN_SIDES..MAX_SIDES) { "Die must have $MIN_SIDES..$MAX_SIDES sides, was $sides" }
        return random.nextInt(1, sides + 1)
    }

    fun rollMany(count: Int, sides: Int): List<Int> {
        require(count in 1..MAX_DICE) { "Can roll 1..$MAX_DICE dice, was $count" }
        return List(count) { roll(sides) }
    }

    fun flipCoin(): CoinSide = if (random.nextBoolean()) CoinSide.HEADS else CoinSide.TAILS

    fun <T> pickFirstPlayer(players: List<T>): T {
        require(players.isNotEmpty()) { "No players to pick from" }
        return players[random.nextInt(players.size)]
    }

    fun <T> shuffleTurnOrder(players: List<T>): List<T> = players.shuffled(random)

    companion object {
        const val MIN_SIDES = 2
        const val MAX_SIDES = 1000
        const val MAX_DICE = 10
        val STANDARD_DICE = listOf(4, 6, 8, 10, 12, 20)
    }
}
