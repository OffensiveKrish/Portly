package com.portly.transfer

import java.util.ArrayDeque

internal class RollingSpeedTracker(
    private val windowMillis: Long = 3_000L,
) {
    private data class Sample(val timestampMs: Long, val deltaBytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var windowBytes: Long = 0L

    fun record(bytes: Long, nowMs: Long = System.currentTimeMillis()) {
        if (bytes > 0L) {
            samples.addLast(Sample(nowMs, bytes))
            windowBytes += bytes
        }
        evictOld(nowMs)
    }

    fun speedBytesPerSec(nowMs: Long = System.currentTimeMillis()): Long {
        evictOld(nowMs)
        if (samples.isEmpty() || windowBytes <= 0L) return 0L
        val oldest = samples.peekFirst()?.timestampMs ?: return 0L
        val elapsedMs = (nowMs - oldest).coerceAtLeast(250L)
        return (windowBytes * 1000L) / elapsedMs
    }

    fun etaSeconds(remainingBytes: Long, nowMs: Long = System.currentTimeMillis()): Long? {
        if (remainingBytes <= 0L) return 0L
        val speed = speedBytesPerSec(nowMs)
        if (speed <= 0L) return null
        return remainingBytes / speed
    }

    private fun evictOld(nowMs: Long) {
        val cutoff = nowMs - windowMillis
        while (true) {
            val head = samples.peekFirst() ?: break
            if (head.timestampMs >= cutoff) break
            samples.removeFirst()
            windowBytes = (windowBytes - head.deltaBytes).coerceAtLeast(0L)
        }
    }
}
