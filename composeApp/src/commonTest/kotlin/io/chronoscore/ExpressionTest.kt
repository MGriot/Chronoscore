package io.chronoscore

import io.chronoscore.engine.Expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpressionTest {
    private fun ev(s: String) = Expression.evaluate(s)

    @Test
    fun precedenceAndParentheses() {
        assertEquals(17, ev("12+5"))
        assertEquals(27, ev("12+5×3"))
        assertEquals(51, ev("(12+5)×3"))
        assertEquals(4, ev("10−12÷2"))
    }

    @Test
    fun unaryMinusAndTruncatingDivision() {
        assertEquals(-5, ev("−5"))
        assertEquals(8, ev("3−−5"))
        assertEquals(-3, ev("−7÷2"))
    }

    @Test
    fun invalidInputIsNull() {
        assertNull(ev(""))
        assertNull(ev("12+"))
        assertNull(ev("(1+2"))
        assertNull(ev("1+2)"))
        assertNull(ev("5÷0"))
        assertNull(ev("×3"))
        assertNull(ev("99999999999"))
    }

    @Test
    fun resultIsClampedToScoreLimits() {
        assertEquals(99_999, ev("99999×99"))
        assertEquals(-99_999, ev("−99999×99"))
    }
}
