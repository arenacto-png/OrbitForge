package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSensitivePathPolicyTest {
    @Test
    fun directSensitivePathsAreRejectedAndOrdinaryPathsAreAllowed() {
        val sensitivePaths = listOf(
            ".env",
            ".env.production",
            "config/local.properties",
            "secrets.properties",
            "service-account-prod.json",
            "firebase-adminsdk-key.json",
            ".netrc",
            ".npmrc",
            ".pypirc",
            ".git-credentials",
            ".git/config",
            "keys/id_ecdsa",
            "certs/private.key",
            "release/upload.jks",
            ".aws/credentials",
            ".azure/accessTokens.json",
            ".gcloud/credentials.db",
            ".kube/config",
            ".docker/config.json",
            "infra/terraform.tfstate",
            "infra/prod.tfvars"
        )

        sensitivePaths.forEach { path ->
            assertTrue("Expected sensitive path to be rejected: $path", WorkspaceSensitivePathPolicy.isSensitive(path))
        }
        assertFalse(WorkspaceSensitivePathPolicy.isSensitive("src/main/App.kt"))
        assertFalse(WorkspaceSensitivePathPolicy.isSensitive("docs/README.md"))
    }

    @Test
    fun inWorkspaceSymlinkAliasToSensitiveFileIsRejected() {
        val root = Files.createTempDirectory("orbitforge-sensitive-link").toFile()
        try {
            val secret = File(root, ".env").apply { writeText("ordinary-looking=value") }
            val alias = File(root, "config.txt")
            try {
                Files.createSymbolicLink(alias.toPath(), secret.toPath())
            } catch (_: Exception) {
                return
            }

            assertTrue(WorkspaceSensitivePathPolicy.isSensitiveTarget(root, alias))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun symlinkAliasToOrdinaryFileIsNotSensitive() {
        val root = Files.createTempDirectory("orbitforge-safe-link").toFile()
        try {
            val target = File(root, "README.md").apply { writeText("hello") }
            val alias = File(root, "readme-link.txt")
            try {
                Files.createSymbolicLink(alias.toPath(), target.toPath())
            } catch (_: Exception) {
                return
            }

            assertFalse(WorkspaceSensitivePathPolicy.isSensitiveTarget(root, alias))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun outsideCanonicalTargetFailsClosed() {
        val temp = Files.createTempDirectory("orbitforge-outside-link").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val outside = File(temp, "outside.txt").apply { writeText("not in workspace") }
            val alias = File(root, "alias.txt")
            try {
                Files.createSymbolicLink(alias.toPath(), outside.toPath())
            } catch (_: Exception) {
                return
            }

            assertTrue(WorkspaceSensitivePathPolicy.isSensitiveTarget(root, alias))
        } finally {
            temp.deleteRecursively()
        }
    }
}
