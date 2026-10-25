package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStepBudgetTest {
    @Test
    fun allowsExactlyTheConfiguredNumberOfSteps() {
        val budget = AgentStepBudget(3)

        assertEquals(1, budget.consume())
        assertEquals(2, budget.consume())
        assertEquals(3, budget.consume())
        assertTrue(budget.isExhausted)
    }

    @Test
    fun refusesAnExtraStepAfterLimit() {
        val budget = AgentStepBudget(1)
        budget.consume()

        try {
            budget.consume()
            throw AssertionError("Expected exhausted budget to reject another step")
        } catch (_: IllegalStateException) {
            assertTrue(budget.isExhausted)
        }
    }

    @Test
    fun startsWithAvailableBudget() {
        assertFalse(AgentStepBudget(2).isExhausted)
    }

    @Test
    fun rejectsNonPositiveLimit() {
        try {
            AgentStepBudget(0)
            throw AssertionError("Expected non-positive limit to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
