package io.github.milankablar.carkaraoke

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureInvariantsTest {
    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
        }
        dir
    }

    private val mainSrcRoot: File by lazy {
        File(repoRoot, "app/src/main/java/io/github/milankablar/carkaraoke")
    }

    @Test
    fun `pure karaoke domain files do not import Android framework packages`() {
        val pureDomainFiles = listOf(
            "karaoke/KaraokeModels.kt",
            "karaoke/EnhancedLrcParser.kt",
            "karaoke/CarLyricsPresenter.kt",
            "karaoke/RefreshGate.kt"
        )
        val forbiddenImport = Regex("""^\s*import\s+(android|androidx)\.""")
        val violations = mutableListOf<String>()

        for (relativePath in pureDomainFiles) {
            val file = File(mainSrcRoot, relativePath)
            assertTrue("Missing expected pure domain file: $relativePath", file.exists())
            file.readLines().forEachIndexed { index, line ->
                if (forbiddenImport.containsMatchIn(line)) {
                    violations += "$relativePath:${index + 1}: $line"
                }
            }
        }

        assertTrue(
            "Pure domain files must stay Android-free:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `ui layer does not import or instantiate OkHttp directly`() {
        val uiDir = File(mainSrcRoot, "ui")
        val forbidden = Regex("""\bokhttp3\b|\bOkHttpClient\b""")
        val violations = mutableListOf<String>()

        uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (forbidden.containsMatchIn(line)) {
                    violations += "${file.relativeTo(repoRoot)}:${index + 1}: $line"
                }
            }
        }

        assertTrue(
            "UI files must delegate HTTP calls to repositories/providers:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `main sources contain no legacy upstream package references`() {
        val mainDir = File(repoRoot, "app/src/main")
        val violations = mutableListOf<String>()

        mainDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if ("com.gululu.aamediamate" in line) {
                        violations += "${file.relativeTo(repoRoot)}:${index + 1}: $line"
                    }
                }
            }

        assertTrue(
            "Upstream package references must not reappear in app/src/main:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `app dependencies are declared through the Gradle version catalog`() {
        val buildFile = File(repoRoot, "app/build.gradle.kts")
        val stringCoordinate = Regex("""^\s*(implementation|testImplementation|androidTestImplementation|debugImplementation)\s*\(\s*"""")
        val violations = mutableListOf<String>()

        buildFile.readLines().forEachIndexed { index, line ->
            if (stringCoordinate.containsMatchIn(line)) {
                violations += "app/build.gradle.kts:${index + 1}: $line"
            }
        }

        assertTrue(
            "Declare dependencies in gradle/libs.versions.toml instead of string coordinates:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
