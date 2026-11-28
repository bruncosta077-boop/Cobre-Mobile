package app.cobre.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun formatsMinutesAndSeconds() {
        assertEquals("0:00", time(0))
        assertEquals("0:09", time(9_999))
        assertEquals("1:01", time(61_000))
        assertEquals("60:00", time(3_600_000))
    }

    @Test fun negativeValuesBecomeZero() {
        assertEquals("0:00", time(-5_000))
    }
}
