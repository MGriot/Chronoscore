package io.chronoscore.model

/** Presets shipped with the app. Saved presets live in the database next to these. */
object BuiltInPresets {
    const val ID_PREFIX = "builtin:"

    val all: List<GamePreset> = listOf(
        GamePreset(
            id = ID_PREFIX + "commander",
            name = "Commander",
            gameName = "Commander",
            playerCount = 4,
            startingScore = 40,
            counters = listOf("Poison"),
            options = MatchOptions(turnTracking = true, mtgExtras = true, winRule = WinRule(WinMode.ELIMINATION, 0)),
        ),
        GamePreset(
            id = ID_PREFIX + "magic",
            name = "Magic 1v1",
            gameName = "Magic",
            playerCount = 2,
            startingScore = 20,
            counters = listOf("Poison"),
            options = MatchOptions(mtgExtras = true, winRule = WinRule(WinMode.ELIMINATION, 0)),
        ),
        GamePreset(
            id = ID_PREFIX + "hearts",
            name = "Hearts",
            gameName = "Hearts",
            playerCount = 4,
            startingScore = 0,
            options = MatchOptions(sheetMode = true, winRule = WinRule(WinMode.TARGET_SCORE, 100, lowestWins = true)),
        ),
        GamePreset(
            id = ID_PREFIX + "race10",
            name = "Race to 10",
            playerCount = 4,
            startingScore = 0,
            options = MatchOptions(turnTracking = true, winRule = WinRule(WinMode.TARGET_SCORE, 10)),
        ),
        GamePreset(
            id = ID_PREFIX + "clock",
            name = "Chess clock",
            playerCount = 2,
            startingScore = 0,
            options = MatchOptions(
                turnTracking = true,
                timeControl = TimeControl(bankSeconds = 10 * 60, bonus = TimeBonus.INCREMENT, bonusSeconds = 5),
            ),
        ),
        GamePreset(
            id = ID_PREFIX + "boardtimer",
            name = "Board game timer",
            playerCount = 4,
            startingScore = 0,
            options = MatchOptions(
                turnTracking = true,
                matchClock = true,
                timeControl = TimeControl(bankSeconds = 20 * 60, bonus = TimeBonus.DELAY, bonusSeconds = 10, outOnTimeout = false),
            ),
        ),
    )

    fun isBuiltIn(preset: GamePreset) = preset.id.startsWith(ID_PREFIX)
}
