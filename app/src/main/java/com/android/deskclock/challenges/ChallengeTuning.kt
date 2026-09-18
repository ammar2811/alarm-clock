/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.deskclock.challenges

/**
 * What each difficulty actually means, per challenge type.
 *
 * Keeping every mapping here means difficulty is data rather than conditionals scattered
 * through the engines, and it makes the numbers reviewable side by side.
 */
object ChallengeTuning {

    // ------------------------------------------------------------------ math

    /**
     * @param operandRange the range single operands are drawn from
     * @param terms how many numbers appear in the expression
     * @param allowMultiply whether multiplication can appear
     * @param multiplicandRange the range used for the factors of a product, kept smaller
     *     than [operandRange] so answers stay solvable in the head
     */
    data class Math(
        val operandRange: IntRange,
        val terms: Int,
        val allowMultiply: Boolean,
        val multiplicandRange: IntRange,
    )

    fun math(difficulty: Difficulty): Math = when (difficulty) {
        Difficulty.EASY -> Math(1..20, terms = 2, allowMultiply = false, 2..5)
        Difficulty.MEDIUM -> Math(10..99, terms = 2, allowMultiply = true, 2..9)
        Difficulty.HARD -> Math(10..99, terms = 3, allowMultiply = true, 2..12)
        Difficulty.EXPERT -> Math(20..199, terms = 3, allowMultiply = true, 11..29)
    }

    // ------------------------------------------------------------------ memory

    /**
     * @param peekMillis how long a non-matching pair stays face up before flipping back
     * @param previewMillis how long the whole board is shown before play starts, 0 for none
     */
    data class Memory(val peekMillis: Long, val previewMillis: Long)

    fun memory(difficulty: Difficulty): Memory = when (difficulty) {
        Difficulty.EASY -> Memory(peekMillis = 1200, previewMillis = 2000)
        Difficulty.MEDIUM -> Memory(peekMillis = 800, previewMillis = 1000)
        Difficulty.HARD -> Memory(peekMillis = 500, previewMillis = 0)
        Difficulty.EXPERT -> Memory(peekMillis = 300, previewMillis = 0)
    }

    // ------------------------------------------------------------------ retype

    /**
     * @param alphabet the characters a target string is drawn from
     * @param caseSensitive whether the typed answer must match letter case
     */
    data class Retype(val alphabet: String, val caseSensitive: Boolean)

    // Ambiguous glyphs are excluded below HARD: telling l from 1 and O from 0 on a phone
    // screen at 6am is a reading test, not a wake-up test.
    private const val LOWER_UNAMBIGUOUS = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER_UNAMBIGUOUS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER_ALL = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER_ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val DIGITS_ALL = "0123456789"
    private const val SYMBOLS = "!@#\$%&*?+="

    fun retype(difficulty: Difficulty): Retype = when (difficulty) {
        Difficulty.EASY ->
            Retype(LOWER_UNAMBIGUOUS, caseSensitive = false)
        Difficulty.MEDIUM ->
            Retype(LOWER_UNAMBIGUOUS + UPPER_UNAMBIGUOUS, caseSensitive = true)
        Difficulty.HARD ->
            Retype(LOWER_ALL + UPPER_ALL + DIGITS, caseSensitive = true)
        Difficulty.EXPERT ->
            Retype(LOWER_ALL + UPPER_ALL + DIGITS_ALL + SYMBOLS, caseSensitive = true)
    }

    // ------------------------------------------------------------------ sequence

    /**
     * @param litMillis how long each shape stays lit during playback
     * @param gapMillis the dark gap between two lit shapes
     */
    data class Sequence(val litMillis: Long, val gapMillis: Long)

    fun sequence(difficulty: Difficulty): Sequence = when (difficulty) {
        Difficulty.EASY -> Sequence(litMillis = 700, gapMillis = 300)
        Difficulty.MEDIUM -> Sequence(litMillis = 500, gapMillis = 200)
        Difficulty.HARD -> Sequence(litMillis = 350, gapMillis = 120)
        Difficulty.EXPERT -> Sequence(litMillis = 250, gapMillis = 80)
    }

    // ------------------------------------------------------------------ photo

    /**
     * @param scoreThreshold minimum detector confidence for a detection to count
     * @param requiredFrames consecutive matching frames needed, which is what actually
     *     suppresses a single lucky frame from a blurry pan around the room
     */
    data class Photo(val scoreThreshold: Float, val requiredFrames: Int)

    fun photo(difficulty: Difficulty): Photo = when (difficulty) {
        Difficulty.EASY -> Photo(scoreThreshold = 0.35f, requiredFrames = 1)
        Difficulty.MEDIUM -> Photo(scoreThreshold = 0.50f, requiredFrames = 2)
        Difficulty.HARD -> Photo(scoreThreshold = 0.60f, requiredFrames = 3)
        Difficulty.EXPERT -> Photo(scoreThreshold = 0.70f, requiredFrames = 4)
    }
}
