package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    @Test fun writeThenReadKeepsPointsPausesAndType() {
        val ev = listOf(
            Mark("start", 1_700_000_000_000), Fix(45.0, 9.0, 1_700_000_000_000, 120.0), Fix(45.001, 9.0, 1_700_000_030_000, 121.5),
            Mark("pause", 1_700_000_030_000), Mark("resume", 1_700_000_090_000),
            Fix(45.002, 9.0, 1_700_000_090_000), Fix(45.003, 9.0, 1_700_000_120_000), Mark("stop", 1_700_000_120_000),
        )
        val back = Gpx.read(Gpx.write(ev, run = false, name = "a < b"))!!
        assertEquals(false, back.run)
        assertEquals(Track.segments(ev), Track.segments(back.events))
        assertEquals(Track.activeMs(ev), Track.activeMs(back.events))
    }

    @Test fun readsStravaStyleGpx() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
             <trk>
              <name>Corsa mattutina</name>
              <type>running</type>
              <trkseg>
               <trkpt lon="9.1900000" lat="45.4640000">
                <ele>120.4</ele>
                <time>2024-05-01T07:00:00Z</time>
               </trkpt>
               <trkpt lat="45.4650000" lon="9.1900000">
                <ele>121.0</ele>
                <time>2024-05-01T07:00:30+00:00</time>
               </trkpt>
              </trkseg>
             </trk>
            </gpx>
        """.trimIndent()
        val g = Gpx.read(gpx)!!
        assertTrue(g.run)
        val fixes = g.events.filterIsInstance<Fix>()
        assertEquals(2, fixes.size)
        assertEquals(Fix(45.464, 9.19, 1_714_546_800_000, 120.4), fixes[0])
        assertEquals(30_000, Track.activeMs(g.events))
    }

    @Test fun rideTypesAreBike() {
        val pts = "<trkseg><trkpt lat=\"1\" lon=\"1\"><time>2024-05-01T07:00:00Z</time></trkpt>" +
            "<trkpt lat=\"1.1\" lon=\"1\"><time>2024-05-01T07:01:00Z</time></trkpt></trkseg>"
        assertEquals(false, Gpx.read("<type>cycling</type>$pts")!!.run)
        assertEquals(false, Gpx.read("<type>1</type>$pts")!!.run)
        assertEquals(true, Gpx.read(pts)!!.run)
    }

    @Test fun rejectsFilesWithoutTimes() {
        assertNull(Gpx.read("<trkseg><trkpt lat=\"1\" lon=\"1\"></trkpt><trkpt lat=\"2\" lon=\"2\"></trkpt></trkseg>"))
        assertNull(Gpx.read("non è un gpx"))
    }
}
