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
 * The rules of the memory challenge: a board of face-down cards in pairs, matched two at a
 * time.
 *
 * This holds all the state and decides what happens on each tap. The fragment renders
 * [cards] and schedules the flip-back after [Tap.Mismatch], so the timing lives in the UI
 * while the rules live here.
 */
class MemoryBoard(pairs: Int, random: Random = Random.Default) {

    /** What a single card looks like to the renderer. */
    data class Card(val symbol: Int, val faceUp: Boolean, val matched: Boolean)

    /** The outcome of tapping a card. */
    sealed interface Tap {
        /** The tap changed nothing, for example a card already face up. */
        object Ignored : Tap

        /** One card is now face up and the board is waiting for its partner. */
        object Revealed : Tap

        /** Two cards matched and stay face up. */
        object Match : Tap

        /**
         * Two cards did not match. The caller should show them, then call [flipBackMismatch]
         * after the peek delay for this difficulty.
         */
        data class Mismatch(val first: Int, val second: Int) : Tap

        /** The last pair matched and the board is complete. */
        object Solved : Tap
    }

    /** Symbol index per position; two positions share each symbol. */
    private val symbols: List<Int> =
        (0 until pairs).flatMap { listOf(it, it) }.shuffled(random)

    private val faceUp = BooleanArray(symbols.size)
    private val matched = BooleanArray(symbols.size)

    /** Set while a mismatched pair is being shown, so further taps are ignored. */
    private var pendingMismatch: Pair<Int, Int>? = null
    private var firstPick: Int? = null

    val size: Int get() = symbols.size

    val cards: List<Card>
        get() = symbols.indices.map { Card(symbols[it], faceUp[it], matched[it]) }

    val isSolved: Boolean get() = matched.all { it }

    /** Reveals every card, for the opening preview on easier difficulties. */
    fun revealAll() {
        for (i in faceUp.indices) faceUp[i] = true
    }

    /** Hides every unmatched card, ending the opening preview. */
    fun hideUnmatched() {
        for (i in faceUp.indices) faceUp[i] = matched[i]
    }

    fun tap(position: Int): Tap {
        if (position !in symbols.indices) return Tap.Ignored
        // Ignore taps while a mismatched pair is still showing, otherwise a fast tapper
        // could reveal a third card and lose track of the board.
        if (pendingMismatch != null) return Tap.Ignored
        if (matched[position] || faceUp[position]) return Tap.Ignored

        faceUp[position] = true

        val first = firstPick
        if (first == null) {
            firstPick = position
            return Tap.Revealed
        }

        firstPick = null
        return if (symbols[first] == symbols[position]) {
            matched[first] = true
            matched[position] = true
            if (isSolved) Tap.Solved else Tap.Match
        } else {
            pendingMismatch = first to position
            Tap.Mismatch(first, position)
        }
    }

    /** Turns the mismatched pair back over once the peek delay has elapsed. */
    fun flipBackMismatch() {
        val pending = pendingMismatch ?: return
        faceUp[pending.first] = false
        faceUp[pending.second] = false
        pendingMismatch = null
    }
}
