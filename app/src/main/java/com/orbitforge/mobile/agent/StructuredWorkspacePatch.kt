package com.orbitforge.mobile.agent

internal data class StructuredTextEdit(val find: String, val replace: String)

internal object StructuredWorkspacePatch {
    const val MAX_FILES = 8
    const val MAX_EDITS_PER_FILE = 8
    const val MAX_TOTAL_EDITS = 24
    const val MAX_EDIT_TEXT_CHARS = 4_000
    const val MAX_FILE_BYTES = 10 * 1024

    /** Applies bounded exact-text replacements; it never creates or deletes files. */
    fun apply(original: String, edits: List<StructuredTextEdit>): String {
        require(original.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Исходный файл превышает лимит патча 10 КБ."
        }
        require(edits.size in 1..MAX_EDITS_PER_FILE) {
            "Для файла нужно указать от 1 до $MAX_EDITS_PER_FILE правок."
        }
        var updated = original
        edits.forEach { edit ->
            require(edit.find.isNotEmpty()) { "Поисковый фрагмент не может быть пустым." }
            require(edit.find.length <= MAX_EDIT_TEXT_CHARS && edit.replace.length <= MAX_EDIT_TEXT_CHARS) {
                "Поисковый и заменяющий фрагменты ограничены 4 000 символами каждый."
            }
            val first = updated.indexOf(edit.find)
            require(first >= 0) { "Поисковый фрагмент не найден; патч отменён." }
            require(updated.indexOf(edit.find, first + 1) < 0) {
                "Поисковый фрагмент встречается более одного раза; патч неоднозначен."
            }
            updated = updated.replaceRange(first, first + edit.find.length, edit.replace)
            require(updated.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
                "Результат превышает лимит патча 10 КБ."
            }
        }
        require(updated != original) { "Патч не содержит изменений." }
        return updated
    }

    /**
     * Returns true when rollback should restore the original content, false when the original is
     * already present, and rejects an intervening edit instead of overwriting it.
     */
    fun shouldRestoreOriginal(
        currentContent: String,
        originalContent: String,
        expectedAppliedContent: String
    ): Boolean {
        require(currentContent == originalContent || currentContent == expectedAppliedContent) {
            "Файл изменён после применения патча; автоматическое восстановление небезопасно."
        }
        return currentContent == expectedAppliedContent
    }
}
