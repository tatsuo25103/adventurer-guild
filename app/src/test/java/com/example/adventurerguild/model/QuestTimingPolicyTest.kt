package com.example.adventurerguild.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestTimingPolicyTest {
    @Test
    fun strictCycleQuestsNeverKeepGraceOrLateSubmissionDays() {
        listOf(
            QuestType.DAILY_QUEST,
            QuestType.WEEKLY_QUEST,
            QuestType.MONTHLY_QUEST
        ).forEach { type ->
            val normalized = Quest(
                type = type,
                gracePeriodDays = 3,
                submissionDeadlineDays = 5
            ).normalizedTimingPolicy()

            assertEquals(0, normalized.gracePeriodDays)
            assertEquals(0, normalized.submissionDeadlineDays)
        }
    }

    @Test
    fun nonCycleQuestsKeepValidGraceAndSubmissionDays() {
        val normalized = Quest(
            type = QuestType.SIDE_QUEST,
            gracePeriodDays = 2,
            submissionDeadlineDays = 4
        ).normalizedTimingPolicy()

        assertEquals(2, normalized.gracePeriodDays)
        assertEquals(4, normalized.submissionDeadlineDays)
    }

    @Test
    fun nonCycleQuestsClampNegativeTimingValues() {
        val normalized = Quest(
            type = QuestType.LIMITED_EVENT_QUEST,
            gracePeriodDays = -2,
            submissionDeadlineDays = -7
        ).normalizedTimingPolicy()

        assertEquals(0, normalized.gracePeriodDays)
        assertEquals(0, normalized.submissionDeadlineDays)
    }
}
