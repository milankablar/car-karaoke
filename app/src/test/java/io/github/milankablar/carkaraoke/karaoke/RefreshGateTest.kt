package io.github.milankablar.carkaraoke.karaoke

import org.junit.Assert.*
import org.junit.Test

class RefreshGateTest {
    @Test fun `continuous callbacks retain the first deadline`() {
        val gate = RefreshGate()
        assertEquals(250L, gate.schedule(0, 250))
        for (now in 20L..240L step 20) assertNull(gate.schedule(now, 250))
        gate.clear()
        assertEquals(250L, gate.schedule(250, 250))
    }
    @Test fun `urgent refresh brings a pending refresh forward`() {
        val gate = RefreshGate()
        assertEquals(750L, gate.schedule(1000, 750))
        assertEquals(0L, gate.schedule(1100, 0))
        assertNull(gate.schedule(1100, 250))
    }
    @Test fun `cancellation allows a new deadline`() {
        val gate = RefreshGate()
        gate.schedule(0, 250); gate.clear()
        assertEquals(500L, gate.schedule(100, 500))
    }
}
