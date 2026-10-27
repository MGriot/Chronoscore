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
        GamePreset(
            id = ID_PREFIX + "catan",
            name = "Catan",
            gameName = "Catan",
            playerCount = 4,
            startingScore = 0,
            options = MatchOptions(turnTracking = true, winRule = WinRule(WinMode.TARGET_SCORE, 10)),
        ),
        GamePreset(
            id = ID_PREFIX + "carcassonne",
            name = "Carcassonne",
            gameName = "Carcassonne",
            playerCount = 4,
            startingScore = 0,
            options = MatchOptions(turnTracking = true),
        ),
        sheet(id = "uno", name = "Uno", seats = 4, target = 500),
        sheet(id = "rummy500", name = "Rummy 500", seats = 4, target = 500),
        // Penalty points: the match ends when a seat reaches 100, and the lowest total wins.
        sheet(id = "skyjo", name = "Skyjo", seats = 4, target = 100, lowestWins = true),
        sheet(id = "raffa", name = "Bocce · Raffa", seats = 2, target = 12, calc = HandCalc.BOCCE),
        sheet(id = "petanque", name = "Pétanque", seats = 2, target = 13, calc = HandCalc.BOCCE),
        // Points are hands won: the hand goes to whoever takes 61 of the 120 card points.
        sheet(id = "briscola", name = "Briscola", seats = 2, target = 2, calc = HandCalc.BRISCOLA),
        sheet(id = "scopa", name = "Scopa", seats = 2, target = 11, calc = HandCalc.SCOPA),
        sheet(id = "scopone", name = "Scopone", seats = 2, target = 11, calc = HandCalc.SCOPA, teamSize = 2),
        // Penalty points: a seat at 101 or more is out, the last one in wins.
        sheet(id = "scala40", name = "Scala 40", seats = 4, target = 101, calc = HandCalc.SCALA40, mode = WinMode.ELIMINATION_ABOVE),
        sheet(id = "burraco", name = "Burraco", seats = 2, target = 2005, calc = HandCalc.BURRACO, teamSize = 2),
        darts(id = "501", name = "Darts 501", rules = DartsRules(DartsGame.X01, start = 501)),
        darts(id = "301", name = "Darts 301", rules = DartsRules(DartsGame.X01, start = 301)),
        darts(id = "cricket", name = "Cricket", rules = DartsRules(DartsGame.CRICKET)),
        darts(id = "clock", name = "Around the Clock", rules = DartsRules(DartsGame.AROUND_THE_CLOCK)),
        darts(id = "shanghai", name = "Shanghai", rules = DartsRules(DartsGame.SHANGHAI, rounds = 7)),
    )

    private fun darts(id: String, name: String, rules: DartsRules) = GamePreset(
        id = ID_PREFIX + "darts-" + id,
        name = name,
        gameName = name,
        playerCount = 2,
        startingScore = if (rules.game == DartsGame.X01) rules.start else 0,
        options = MatchOptions(turnTracking = true, darts = rules),
    )

    /** A round-sheet game that starts at 0, with a hand calculator when [calc] is set. */
    private fun sheet(
        id: String,
        name: String,
        seats: Int,
        target: Int,
        calc: HandCalc? = null,
        mode: WinMode = WinMode.TARGET_SCORE,
        teamSize: Int? = null,
        lowestWins: Boolean = false,
    ) = GamePreset(
        id = ID_PREFIX + id,
        name = name,
        gameName = name.substringAfter("· "),
        playerCount = seats,
        startingScore = 0,
        options = MatchOptions(sheetMode = true, winRule = WinRule(mode, target, lowestWins), handCalculator = calc),
        teamSize = teamSize,
    )

    fun isBuiltIn(preset: GamePreset) = preset.id.startsWith(ID_PREFIX)
}
