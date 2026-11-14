package com.anthonyla.hotaru.domain

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val NANOS_PER_TICK: Long = 100L

/** Jellyfin time unit: 1 tick = 100 ns. All tick conversions live here. */
@JvmInline
@Serializable
value class Ticks(val value: Long) : Comparable<Ticks> {

    fun toDuration(): Duration = (value * NANOS_PER_TICK).nanoseconds

    val milliseconds: Long get() = value / TICKS_PER_MILLISECOND

    operator fun plus(other: Ticks): Ticks = Ticks(value + other.value)
    operator fun minus(other: Ticks): Ticks = Ticks(value - other.value)
    operator fun plus(duration: Duration): Ticks = this + duration.ticks
    operator fun minus(duration: Duration): Ticks = this - duration.ticks
    operator fun times(factor: Float): Ticks = Ticks((value * factor.toDouble()).toLong())

    override fun compareTo(other: Ticks): Int = value.compareTo(other.value)

    /** Fraction of [total] elapsed, clamped to 0..1. Zero total yields 0. */
    fun fractionOf(total: Ticks): Double =
        if (total.value <= 0L) 0.0 else (value.toDouble() / total.value).coerceIn(0.0, 1.0)

    companion object {
        private const val TICKS_PER_MILLISECOND: Long = 10_000L

        val ZERO: Ticks = Ticks(0L)

        fun ofMillis(millis: Long): Ticks = Ticks(millis * TICKS_PER_MILLISECOND)
    }
}

val Duration.ticks: Ticks get() = Ticks(inWholeNanoseconds / NANOS_PER_TICK)
