package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExecutionBudgetTest {
    @Test
    fun reservesFinalModelRoundForCompletion() {
        val budget = AgentExecutionBudget(maxModelRounds = 3)

        assertEquals(1, budget.beginModelRound())
        assertTrue(budget.canExecuteToolCalls())
        assertEquals(2, budget.beginModelRound())
        assertTrue(budget.canExecuteToolCalls())
        assertEquals(3, budget.beginModelRound())
        assertFalse(budget.canExecuteToolCalls())
    }

    @Test
    fun rejectsRequestsBeyondTheConfiguredRoundLimit() {
        val budget = AgentExecutionBudget(maxModelRounds = 2)
        budget.beginModelRound()
        budget.beginModelRound()

        try {
            budget.beginModelRound()
            throw AssertionError("A request beyond the round budget must be rejected")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }

    @Test
    fun rejectsNonPositiveRoundLimit() {
        try {
            AgentExecutionBudget(maxModelRounds = 0)
            throw AssertionError("A zero round budget must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
