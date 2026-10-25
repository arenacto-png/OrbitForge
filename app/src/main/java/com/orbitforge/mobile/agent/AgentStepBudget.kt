package com.orbitforge.mobile.agent

/**
 * Tracks the number of model round-trips allowed for one agent task.
 *
 * Keeping the limit in a small pure class makes the stop condition testable without a live provider.
 */
internal class AgentStepBudget(private val limit: Int) {
    private var consumedSteps = 0

    init {
        require(limit > 0) { "Лимит шагов агента должен быть положительным." }
    }

    val isExhausted: Boolean
        get() = consumedSteps >= limit

    fun consume(): Int {
        check(!isExhausted) { "Агент достиг лимита $limit шагов." }
        consumedSteps += 1
        return consumedSteps
    }
}
