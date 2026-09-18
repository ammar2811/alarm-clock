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

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.deskclock.R

/**
 * Turns challenge configs into the words and icons the settings screens show.
 *
 * Kept apart from the model so the model itself stays free of Android dependencies and
 * testable on the JVM.
 */
object ChallengeSummary {

    @StringRes
    fun nameOf(kind: ChallengeKind): Int = when (kind) {
        ChallengeKind.MATH -> R.string.challenge_name_math
        ChallengeKind.PHOTO -> R.string.challenge_name_photo
        ChallengeKind.MEMORY -> R.string.challenge_name_memory
        ChallengeKind.RETYPE -> R.string.challenge_name_retype
        ChallengeKind.SEQUENCE -> R.string.challenge_name_sequence
    }

    @StringRes
    fun aboutOf(kind: ChallengeKind): Int = when (kind) {
        ChallengeKind.MATH -> R.string.challenge_about_math
        ChallengeKind.PHOTO -> R.string.challenge_about_photo
        ChallengeKind.MEMORY -> R.string.challenge_about_memory
        ChallengeKind.RETYPE -> R.string.challenge_about_retype
        ChallengeKind.SEQUENCE -> R.string.challenge_about_sequence
    }

    @DrawableRes
    fun iconOf(kind: ChallengeKind): Int = when (kind) {
        ChallengeKind.MATH -> R.drawable.ic_challenge_math
        ChallengeKind.PHOTO -> R.drawable.ic_challenge_photo
        ChallengeKind.MEMORY -> R.drawable.ic_challenge_memory
        ChallengeKind.RETYPE -> R.drawable.ic_challenge_retype
        ChallengeKind.SEQUENCE -> R.drawable.ic_challenge_sequence
    }

    @StringRes
    fun nameOf(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }

    /** "Medium, 2 equations", for the card under the challenge's name. */
    fun describe(context: Context, config: ChallengeConfig): String {
        val parts = mutableListOf(context.getString(nameOf(config.difficulty)))
        val resources = context.resources

        when (config) {
            is MathChallenge -> parts += resources.getQuantityString(
                    R.plurals.challenge_summary_equations, config.equations, config.equations)

            is MemoryChallenge -> parts += resources.getQuantityString(
                    R.plurals.challenge_summary_pairs, config.pairs, config.pairs)

            is RetypeChallenge -> {
                parts += resources.getQuantityString(
                        R.plurals.challenge_summary_characters, config.length, config.length)
                if (config.rounds > 1) {
                    parts += resources.getQuantityString(
                            R.plurals.challenge_summary_rounds, config.rounds, config.rounds)
                }
            }

            is SequenceChallenge -> {
                parts += resources.getQuantityString(
                        R.plurals.challenge_summary_shapes, config.shapes, config.shapes)
                parts += resources.getQuantityString(
                        R.plurals.challenge_summary_length, config.length, config.length)
            }

            is PhotoChallenge -> {
                parts += config.targets.joinToString(
                        context.getString(R.string.challenge_summary_separator)) {
                    it.replaceFirstChar(Char::uppercase)
                }
                if (config.photos > 1) {
                    parts += resources.getQuantityString(
                            R.plurals.challenge_summary_photos, config.photos, config.photos)
                }
            }
        }

        return parts.joinToString(context.getString(R.string.challenge_summary_separator))
    }

    /**
     * One line for the alarm editor's Challenges row: "Off", the names of what is
     * configured, or the first couple of names with a count of the rest.
     */
    fun describeList(context: Context, challenges: List<ChallengeConfig>): String {
        // Names the row itself when empty, since "Off" on its own says nothing about what
        // is off. Matches how the label row shows its own name until one is set.
        if (challenges.isEmpty()) return context.getString(R.string.challenges_title)

        val separator = context.getString(R.string.challenge_summary_separator)
        val names = challenges.map { context.getString(nameOf(it.kind)) }
        if (names.size <= MAX_NAMES_IN_ROW) return names.joinToString(separator)

        val shown = names.take(MAX_NAMES_IN_ROW).joinToString(separator)
        return context.getString(R.string.challenges_and_more, shown,
                names.size - MAX_NAMES_IN_ROW)
    }

    private const val MAX_NAMES_IN_ROW = 2
}
