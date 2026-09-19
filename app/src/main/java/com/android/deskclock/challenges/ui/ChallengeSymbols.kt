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

import android.content.Context
import androidx.annotation.ColorInt

import com.android.deskclock.R

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

    /** How many distinct symbols are available. */
    val count: Int get() = GLYPHS.size

    fun glyph(index: Int): String = GLYPHS[index % GLYPHS.size]

    /**
     * The colour for a symbol. These cannot be theme roles, because the whole point is
     * that the ten are tellable apart from each other, so they live in a resource array
     * that can be qualified for night instead.
     */
    @ColorInt
    fun color(context: Context, index: Int): Int {
        val colors = context.resources.obtainTypedArray(R.array.challenge_symbol_colors)
        try {
            return colors.getColor(index % colors.length(), 0)
        } finally {
            colors.recycle()
        }
    }
}
