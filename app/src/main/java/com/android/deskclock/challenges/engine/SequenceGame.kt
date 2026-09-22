/*
 * Copyright (C) 2026 Ammar Siddiqui
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.challenges.engine

import kotlin.random.Random

/**
 * The rules of the repeat-the-sequence challenge: shapes light up in an order, and the
 * sequence must be tapped back.
 *
 * Replays are unlimited by design. The challenge is meant to make you sit up and pay
 * attention, not to punish you for missing the first playback while half asleep.
 */
class SequenceGame(
    val shapeCount: Int,
    length: Int,
    random: Random = Random.Default,
) {

    /** The outcome of tapping a shape. */
    sealed interface Tap {
        /** Correct so far, with [matched] of [total] entered. */
        data class Correct(val matched: Int, val total: Int) : Tap

        /** Wrong shape. Progress resets and the sequence must be entered again. */
        object Wrong : Tap

        /** The whole sequence has been entered correctly. */
        object Complete : Tap
    }

    /** The shape indices to reproduce, in order. */
    val sequence: List<Int> = List(length) { random.nextInt(shapeCount) }

    private var progress = 0

    /** How many entries have been matched so far in the current attempt. */
    val matched: Int get() = progress

    val isComplete: Boolean get() = progress == sequence.size

    fun tap(shape: Int): Tap {
        if (isComplete) return Tap.Complete

        if (shape != sequence[progress]) {
            // Reset rather than fail outright: the sequence is unchanged and can be
            // replayed, so a wrong tap costs time instead of ending the challenge.
            progress = 0
            return Tap.Wrong
        }

        progress++
        return if (isComplete) Tap.Complete else Tap.Correct(progress, sequence.size)
    }

    /** Clears entry progress, for when the user asks to see the sequence again. */
    fun restartEntry() {
        progress = 0
    }
}
