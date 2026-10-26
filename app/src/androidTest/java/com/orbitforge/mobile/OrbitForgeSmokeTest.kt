package com.orbitforge.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/** UI smoke tests run on an Android emulator in GitHub Actions. */
class OrbitForgeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndMainDestinationsOpen() {
        composeRule.onNodeWithText("OrbitForge").assertIsDisplayed()

        composeRule.onNodeWithText("Проекты").performClick()
        composeRule.onNodeWithText("Проекты и файлы").assertIsDisplayed()

        composeRule.onNodeWithText("Открыть встроенный терминал").performClick()
        composeRule.onNodeWithText("Ограниченный терминал").assertIsDisplayed()
        composeRule.onNodeWithText("Назад к проектам").performClick()
        composeRule.onNodeWithText("Проекты и файлы").assertIsDisplayed()

        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onNodeWithText("Базовый URL API").assertIsDisplayed()
    }

    @Test
    fun diagnosticsCanBeOpenedAndCopied() {
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onNodeWithText("Открыть логи и диагностику").performScrollTo().performClick()

        composeRule.onNodeWithText("Логи и отчёт об ошибках").assertIsDisplayed()
        composeRule.onNodeWithText("Копировать").performClick()
        composeRule.onNodeWithText("Отчёт скопирован в буфер обмена.").assertIsDisplayed()
    }

    @Test
    fun settingsRejectPlainHttpEndpoint() {
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.onNodeWithText("Базовый URL API").performScrollTo().assertIsDisplayed()
        val endpointField = composeRule.onAllNodes(hasSetTextAction())[0]
        endpointField.performTextClearance()
        endpointField.performTextInput("http://invalid.example/v1")
        composeRule.onNodeWithText("Сохранить настройки").performScrollTo().performClick()
        composeRule.onNodeWithText("Адрес должен начинаться с https://").assertIsDisplayed()
    }
}
