package io.github.milankablar.carkaraoke.diagnostics

enum class DiagnosticLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

enum class DiagnosticModule {
    MEDIA,
    LYRICS,
    NETWORK,
    SYSTEM
}

data class DiagnosticEvent(
    val timestampMs: Long,
    val level: DiagnosticLevel,
    val module: DiagnosticModule,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val throwable: String? = null
)
