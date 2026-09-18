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

package com.android.deskclock.challenges.ui

import android.graphics.Color

/**
 * The shapes the memory and sequence challenges are built from.
 *
 * Each is a distinct glyph in a distinct colour, so the pairs stay tellable apart for
 * someone half awake and for anyone who cannot rely on colour alone. The set is large
 * enough for the maximum board of ten pairs.
 */
object ChallengeSymbols {

    private val GLYPHS = listOf(
        "●", // circle
        "■", // square
        "▲", // triangle
        "★", // star
        "◆", // diamond
        "♥", // heart
        "⬢", // hexagon
        "✚", // cross
        "◐", // half circle
        "☀", // sun
    )

    private val COLORS = listOf(
        Color.parseColor("#FF7043"),
        Color.parseColor("#42A5F5"),
        Color.parseColor("#66BB6A"),
        Color.parseColor("#FFCA28"),
        Color.parseColor("#AB47BC"),
        Color.parseColor("#EC407A"),
        Color.parseColor("#26C6DA"),
        Color.parseColor("#D4E157"),
        Color.parseColor("#8D6E63"),
        Color.parseColor("#FFFFFF"),
    )

    /** How many distinct symbols are available. */
    val count: Int get() = GLYPHS.size

    fun glyph(index: Int): String = GLYPHS[index % GLYPHS.size]

    fun color(index: Int): Int = COLORS[index % COLORS.size]
}
