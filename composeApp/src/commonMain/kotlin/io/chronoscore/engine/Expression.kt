package io.chronoscore.engine

import io.chronoscore.model.Limits

/**
 * Tiny calculator for the keypad: integers with `+ − × ÷ ( )`, unary minus and the usual
 * precedence. Division truncates toward zero. Returns null for anything that is not a
 * complete, valid expression (empty, dangling operator, unbalanced parentheses, ÷ 0, or a
 * value beyond ±[MAX_INTERMEDIATE]).
 */
object Expression {
    /** Guards against runaway intermediate results; the final score is clamped by the caller. */
    private const val MAX_INTERMEDIATE = 1_000_000_000L

    fun evaluate(text: String): Int? = try {
        val p = Parser(text.filterNot { it.isWhitespace() })
        val v = p.expr()
        if (p.atEnd()) v.coerceIn(Limits.MIN_VALUE.toLong(), Limits.MAX_VALUE.toLong()).toInt() else null
    } catch (_: IllegalArgumentException) {
        null
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        private fun peek(): Char? = s.getOrNull(i)

        private fun check(v: Long): Long {
            require(v in -MAX_INTERMEDIATE..MAX_INTERMEDIATE)
            return v
        }

        fun expr(): Long {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v = check(v + term()) }
                    '-', '−' -> { i++; v = check(v - term()) }
                    else -> return v
                }
            }
        }

        private fun term(): Long {
            var v = unary()
            while (true) {
                when (peek()) {
                    '*', '×', 'x' -> { i++; v = check(v * unary()) }
                    '/', '÷' -> {
                        i++
                        val d = unary()
                        require(d != 0L)
                        v /= d
                    }
                    else -> return v
                }
            }
        }

        private fun unary(): Long = when (peek()) {
            '-', '−' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> atom()
        }

        private fun atom(): Long {
            if (peek() == '(') {
                i++
                val v = expr()
                require(peek() == ')')
                i++
                return v
            }
            val start = i
            while (peek()?.isDigit() == true) i++
            require(i > start && i - start <= 10)
            return check(s.substring(start, i).toLong())
        }
    }
}

/**
 * What was typed on the keypad: an equation that sets the value, or — when it starts with
 * `+` or `−` — a change to the current value ("+5", "−3×2"). [value] is the new value for a
 * set, or the change when [relative].
 */
data class Entry(val relative: Boolean, val value: Int) {
    /** The value a seat at [current] ends up with, within the score limits. */
    fun next(current: Int): Int =
        (if (relative) current + value else value).coerceIn(Limits.MIN_VALUE, Limits.MAX_VALUE)

    /** The equation that stands for this entry's result: "42", or "+5" / "−3" for a change. */
    fun folded(): String = when {
        !relative -> value.toString().replace('-', '−')
        value >= 0 -> "+$value"
        else -> "−${-value}"
    }

    companion object {
        /** Null while [text] is empty or not a complete equation. */
        fun parse(text: String): Entry? {
            val t = text.trim()
            if (t.isEmpty()) return null
            val relative = t.first() in "+-−"
            val v = Expression.evaluate(if (relative) "0$t" else t) ?: return null
            return Entry(relative, v)
        }
    }
}
