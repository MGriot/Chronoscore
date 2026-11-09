package io.chronoscore

import io.chronoscore.engine.Entry
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

    @Test
    fun leadingSignChangesTheCurrentValueOtherwiseItSets() {
        assertEquals(Entry(false, 27), Entry.parse("12+5×3"))
        assertEquals(Entry(true, 5), Entry.parse("+5"))
        assertEquals(Entry(true, -6), Entry.parse("−3×2"))
        assertEquals(55, Entry.parse("+5")!!.next(50))
        assertEquals(27, Entry.parse("12+5×3")!!.next(50))
        assertEquals(-5, Entry.parse("0−5")!!.next(50))
        assertNull(Entry.parse(""))
        assertNull(Entry.parse("×3"))
        assertNull(Entry.parse("+"))
        assertEquals("+5", Entry.parse("2+3")!!.copy(relative = true).folded())
        assertEquals("−6", Entry.parse("−3×2")!!.folded())
    }
}
