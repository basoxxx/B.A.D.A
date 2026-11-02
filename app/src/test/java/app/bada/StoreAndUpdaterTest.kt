package app.bada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreAndUpdaterTest {
    @Test fun backupAcceptsOnlyTrackNames() {
        assertTrue(TrackStore.NAME.matches("1730000000000_RUN.csv"))
        assertTrue(TrackStore.NAME.matches("1730000000000_BIKE.csv"))
        assertFalse(TrackStore.NAME.matches("../1730000000000_RUN.csv"))
        assertFalse(TrackStore.NAME.matches("tracks/1730000000000_RUN.csv"))
        assertFalse(TrackStore.NAME.matches("1730000000000_RUN.csv.tmp"))
        assertFalse(TrackStore.NAME.matches("evil.so"))
    }

    @Test fun releaseCodeFromTag() {
        assertEquals(57, Updater.releaseCode("v1.0.57"))
        assertNull(Updater.releaseCode("latest"))
    }
}
