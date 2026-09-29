package com.trashhotdog123.ereader

import android.os.SystemClock
import kotlin.math.roundToInt

class ReadingTelemetry(initialWpm: Int) {
    private val baseline = initialWpm.coerceIn(80, 1200)
    private var liveWpm = baseline.toDouble()
    private var lastLocation = -1
    private var lastAt = 0L
    private var lastDirection = 0
    private var lastForwardWords = 0
    private var validForwardWords = 0
    private var startedAt = SystemClock.elapsedRealtime()
    private var ignoredCorrections = 0

    fun reset(location: Int) {
        lastLocation = location
        lastAt = SystemClock.elapsedRealtime()
    }

    fun moveTo(location: Int, wordsPerPage: Int) {
        val now = SystemClock.elapsedRealtime()
        if (lastLocation < 0) { reset(location); return }
        val delta = location - lastLocation
        if (delta == 0) return
        val elapsed = now - lastAt
        val direction = if (delta > 0) 1 else -1
        if (elapsed in 1..1400 && direction != lastDirection && lastDirection != 0) {
            validForwardWords = (validForwardWords - lastForwardWords).coerceAtLeast(0)
            ignoredCorrections++
            lastForwardWords = 0
            lastLocation = location
            lastAt = now
            lastDirection = direction
            return
        }
        if (elapsed < 350) {
            lastLocation = location
            lastAt = now
            lastDirection = direction
            return
        }
        if (delta > 0) {
            val words = delta * wordsPerPage
            validForwardWords += words
            lastForwardWords = words
            val minutes = (elapsed / 60000.0).coerceAtLeast(0.001)
            val sample = (words / minutes).coerceIn(50.0, 1500.0)
            liveWpm = liveWpm * 0.7 + sample * 0.3
        }
        lastLocation = location
        lastAt = now
        lastDirection = direction
    }

    fun currentWpm(): Int {
        if (validForwardWords < 80) return baseline
        val minutes = ((SystemClock.elapsedRealtime() - startedAt) / 60000.0).coerceAtLeast(0.01)
        val observed = (validForwardWords / minutes).coerceIn(50.0, 1500.0)
        return ((baseline * 0.65) + (liveWpm * 0.25) + (observed * 0.10))
            .roundToInt().coerceIn(80, 1200)
    }

    fun corrections(): Int = ignoredCorrections
}
