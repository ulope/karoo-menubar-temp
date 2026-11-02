package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsTest {

    @Before
    fun setUp() = Diagnostics.clear()

    @Test
    fun `merges entries that only differ in the values shown`() {
        Diagnostics.record("SHOWN \"30°\" at [204,9-260,58]\nride time \"0:00:03\"\n  #7 ViewPager nodes 91 labels 18 in gap 0")
        Diagnostics.record("SHOWN \"31°\" at [204,9-260,58]\nride time \"0:00:04\"\n  #7 ViewPager nodes 92 labels 17 in gap 0")
        val entries = Diagnostics.entries()
        assertEquals(1, entries.size)
        assertTrue(entries[0].contains("\"31°\""))
    }

    @Test
    fun `keeps entries where something moved over the status row`() {
        Diagnostics.record("SHOWN \"30°\"\n  ·FrameLayout \"\" [0,740-480,800]\n  #3 FrameLayout nodes 5 labels 0 in gap 0")
        Diagnostics.record("SHOWN \"30°\"\n  ·FrameLayout \"\" [0,0-480,800]\n  #3 FrameLayout nodes 5 labels 0 in gap 0")
        Diagnostics.record("SHOWN \"30°\"\n  ·FrameLayout \"\" [0,0-480,800]\n  #3 FrameLayout nodes 5 labels 0 in gap 2")
        assertEquals(3, Diagnostics.entries().size)
    }
}
