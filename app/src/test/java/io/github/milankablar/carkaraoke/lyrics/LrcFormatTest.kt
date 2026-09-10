package io.github.milankablar.carkaraoke.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LrcFormatTest {
    @Test fun shiftsEveryTimestampWithoutChangingText() {
        assertEquals("[00:01.50][00:02.75]line", LrcFormat.shift("[00:01][00:02.25]line", 500))
        assertEquals("[00:00.00]line", LrcFormat.shift("[00:00.10]line", -500))
    }
    @Test fun roundingCarriesToNextMinute() {
        assertEquals("[01:00.00]line", LrcFormat.shift("[00:59.999]line", 0))
    }
    @Test fun malformedNumbersAreIgnored() {
        val input = "[99999999999999999999999:01.00]line"
        assertEquals(input, LrcFormat.shift(input, 500))
    }
    @Test fun decimalSeparatorDoesNotFollowDeviceLocale() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("[00:01.50]line", LrcFormat.shift("[00:01]line", 500))
        } finally { Locale.setDefault(locale) }
    }
}
