package com.example.adventurerguild.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenTestAccountUnlockTrackerTest {
    @Test
    fun unlocksAfterSevenTapsWithinFiveSeconds() {
        val tracker = HiddenTestAccountUnlockTracker()

        repeat(6) { index ->
            assertFalse(tracker.registerTap(index * 500L))
        }

        assertTrue(tracker.registerTap(3_000L))
    }

    @Test
    fun resetsWhenTapWindowExpires() {
        val tracker = HiddenTestAccountUnlockTracker()

        repeat(6) { index ->
            assertFalse(tracker.registerTap(index * 500L))
        }

        assertFalse(tracker.registerTap(8_000L))
    }

    @Test
    fun requiresFullSequenceAgainAfterUnlocking() {
        val tracker = HiddenTestAccountUnlockTracker()

        repeat(6) { index -> tracker.registerTap(index * 500L) }
        assertTrue(tracker.registerTap(3_000L))

        repeat(6) { index ->
            assertFalse(tracker.registerTap(4_000L + index * 100L))
        }
        assertTrue(tracker.registerTap(4_600L))
    }
}
