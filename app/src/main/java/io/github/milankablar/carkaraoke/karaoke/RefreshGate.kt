package io.github.milankablar.carkaraoke.karaoke

/** Keeps the earliest refresh deadline so a busy player's callbacks cannot starve reads. */
internal class RefreshGate {
    private var dueAt = Long.MAX_VALUE
    fun schedule(nowMs: Long, requestedDelayMs: Long): Long? {
        val delay = requestedDelayMs.coerceAtLeast(0)
        val requestedAt = nowMs + delay
        if (dueAt <= requestedAt) return null
        dueAt = requestedAt
        return delay
    }
    fun clear() { dueAt = Long.MAX_VALUE }
}
