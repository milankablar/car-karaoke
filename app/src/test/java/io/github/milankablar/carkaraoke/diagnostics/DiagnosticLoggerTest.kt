package io.github.milankablar.carkaraoke.diagnostics

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DiagnosticLoggerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        DiagnosticLogger.clear(context)
        context.getSharedPreferences("diagnostic_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `pruneEvents keeps recent 500 events`() {
        val now = 2_000_000L
        val events = (0 until 550).map { index ->
            DiagnosticEvent(
                timestampMs = now - (550 - index),
                level = DiagnosticLevel.INFO,
                module = DiagnosticModule.SYSTEM,
                message = "event-$index"
            )
        }

        val pruned = DiagnosticLogger.pruneEvents(events, now)

        assertEquals(500, pruned.size)
        assertEquals("event-50", pruned.first().message)
        assertEquals("event-549", pruned.last().message)
    }

    @Test
    fun `debug events are ignored until detailed diagnostics is enabled`() {
        DiagnosticLogger.debug(context, DiagnosticModule.MEDIA, "hidden debug")

        assertTrue(DiagnosticLogger.getEvents(context).isEmpty())

        DiagnosticLogger.enableDebugFor(context, durationMs = 60_000L)
        DiagnosticLogger.debug(context, DiagnosticModule.MEDIA, "visible debug")

        val messages = DiagnosticLogger.getEvents(context).map { it.message }
        assertFalse(messages.contains("hidden debug"))
        assertTrue(messages.contains("Detailed diagnostics enabled"))
        assertTrue(messages.contains("visible debug"))
    }

    @Test
    fun `issue report redacts secrets and sanitizes urls`() {
        DiagnosticLogger.info(
            context,
            DiagnosticModule.NETWORK,
            "Request finished",
            mapOf(
                "url" to "http://192.168.1.2:28883/lyrics?title=Song&token=secret-token",
                "Authorization" to "Bearer secret-token",
                "apiKey" to "secret-api-key",
                "status" to 200
            )
        )

        val report = DiagnosticLogger.buildIssueReport(context)

        assertTrue(report.contains("http://192.168.1.2:28883/lyrics"))
        assertTrue(report.contains("Authorization=[redacted]"))
        assertTrue(report.contains("apiKey=[redacted]"))
        assertTrue(report.contains("status=200"))
        assertFalse(report.contains("secret-token"))
        assertFalse(report.contains("secret-api-key"))
        assertFalse(report.contains("title=Song"))
    }
}
