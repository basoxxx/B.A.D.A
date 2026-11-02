package app.bada

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun distance() = assertEquals("5,23 km", Format.distance(5234.0))
    @Test fun shortDuration() = assertEquals("28:14", Format.duration(1_694_000))
    @Test fun longDuration() = assertEquals("1:02:03", Format.duration(3_723_000))
    @Test fun pace() = assertEquals("5:24 /km", Format.pace(5000.0, 1_620_000))
    @Test fun paceTooShort() = assertEquals("--", Format.pace(5.0, 10_000))
    @Test fun speed() = assertEquals("24,0 km/h", Format.speed(24_000.0, 3_600_000))
}
