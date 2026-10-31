package com.orbitforge.mobile.agent

/**
 * Tracks model request rounds. Tool calls from the final permitted response are not executed:
 * the agent must reserve that last response for a final answer rather than perform side effects
 * that it cannot report back to the model.
 */
internal class AgentExecutionBudget(
    private val maxModelRounds: Int = MAX_MODEL_ROUNDS
) {
    private var startedRounds = 0

    init {
        require(maxModelRounds > 0) { "Лимит раундов должен быть положительным." }
    }

    fun beginModelRound(): Int {
        check(startedRounds < maxModelRounds) {
            "Агент достиг лимита $maxModelRounds запросов к модели."
        }
        startedRounds += 1
        return startedRounds
    }

    fun canExecuteToolCalls(): Boolean = startedRounds < maxModelRounds

    companion object {
        const val MAX_MODEL_ROUNDS = 8
    }
}
