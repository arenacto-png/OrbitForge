package com.orbitforge.mobile.agent

import android.content.Context
import java.io.File

data class LoadedSkill(val name: String, val relativePath: String, val content: String)

/**
 * Skills are plain-text guidance stored in the private workspace. They are untrusted project
 * content: they may guide coding style but never override application security boundaries.
 */
class SkillManager(context: Context) {
    private val workspace = File(context.filesDir, "orbitforge-workspace")
    private val skillsRoot = File(workspace, "skills")

    fun ensureStarterSkills() {
        val androidSkill = File(skillsRoot, "android-kotlin/SKILL.md")
        if (!androidSkill.exists()) {
            androidSkill.parentFile?.mkdirs()
            androidSkill.writeText(
                listOf(
                    "# Android Kotlin Development",
                    "Use Kotlin idioms, small functions, clear names, and lifecycle-aware coroutines.",
                    "Prefer Jetpack Compose for new UI. Keep UI state separate from network and file operations.",
                    "Do not claim a build or test passed unless it was actually run. Explain any unverified assumptions.",
                    "Add or update tests for logic changes. Never put API keys or tokens in source code or logs."
                ).joinToString("\n") + "\n",
                Charsets.UTF_8
            )
        }
        val reviewSkill = File(skillsRoot, "code-review/SKILL.md")
        if (!reviewSkill.exists()) {
            reviewSkill.parentFile?.mkdirs()
            reviewSkill.writeText(
                listOf(
                    "# Code Review",
                    "Review correctness, edge cases, security, resource handling, and test coverage.",
                    "Prioritize actionable findings with file paths and concise explanations.",
                    "Check that file operations stay within the approved workspace and that secrets are not logged.",
                    "Do not modify files unless the user requested changes and the app approval dialog accepts the write."
                ).joinToString("\n") + "\n",
                Charsets.UTF_8
            )
        }
    }

    fun loadEnabledSkills(maxSkills: Int = 5, maxBytesEach: Long = 12 * 1024): List<LoadedSkill> {
        ensureStarterSkills()
        return skillsRoot.walkTopDown()
            .filter { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
            .sortedBy { it.relativeTo(skillsRoot).path }
            .take(maxSkills)
            .mapNotNull { file ->
                if (file.length() > maxBytesEach) return@mapNotNull null
                val relative = file.relativeTo(workspace).path.replace(File.separatorChar, '/')
                LoadedSkill(
                    name = file.parentFile?.name ?: "skill",
                    relativePath = relative,
                    content = file.readText(Charsets.UTF_8)
                )
            }
            .toList()
    }
}
