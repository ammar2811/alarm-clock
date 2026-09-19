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

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Generates the stable identity an alarm's challenge list uses for edit and reorder. */
fun newChallengeId(): String = UUID.randomUUID().toString()

/**
 * One configured challenge that must be completed before an alarm may be dismissed.
 *
 * An alarm holds an ordered list of these. Every field has a default so that a row written
 * by an older or newer build still decodes, and [normalized] clamps stored numbers back into
 * their supported range so a hand-edited or corrupted row cannot produce an unplayable
 * challenge.
 *
 * This file is deliberately free of Android dependencies so the model and the engines built
 * on it can be unit tested on the JVM.
 */
@Serializable
sealed interface ChallengeConfig {
    /** Stable within one alarm; survives reordering and editing. */
    val id: String

    val difficulty: Difficulty

    /** Which kind of challenge this is, for icons, titles and routing. */
    val kind: ChallengeKind

    /** Returns a copy with every numeric field clamped into its supported range. */
    fun normalized(): ChallengeConfig
}

/** The challenge types, in the order the picker offers them. */
enum class ChallengeKind {
    MATH,
    PHOTO,
    MEMORY,
    RETYPE,
    SEQUENCE,
}

/** Solve a number of arithmetic problems. */
@Serializable
@SerialName("math")
data class MathChallenge(
    override val id: String = newChallengeId(),
    val equations: Int = 3,
    override val difficulty: Difficulty = Difficulty.DEFAULT,
) : ChallengeConfig {
    override val kind: ChallengeKind get() = ChallengeKind.MATH

    override fun normalized(): MathChallenge =
        copy(equations = equations.coerceIn(EQUATIONS))

    companion object {
        val EQUATIONS = 1..10
    }
}

/**
 * Photograph one of [targets], verified on device by object recognition. Targets are
 * detector label names; see [CocoLabels].
 */
@Serializable
@SerialName("photo")
data class PhotoChallenge(
    override val id: String = newChallengeId(),
    val targets: List<String> = listOf(CocoLabels.DEFAULT_TARGET),
    val photos: Int = 1,
    override val difficulty: Difficulty = Difficulty.DEFAULT,
) : ChallengeConfig {
    override val kind: ChallengeKind get() = ChallengeKind.PHOTO

    override fun normalized(): PhotoChallenge {
        // Drop labels the bundled model cannot detect, so a challenge can never be
        // impossible to satisfy. Fall back to the default rather than an empty target set.
        val known = targets.filter(CocoLabels::isKnown).distinct()
        val usable = known.ifEmpty { listOf(CocoLabels.DEFAULT_TARGET) }
        return copy(
            targets = usable,
            // Every photo has to be a different target, so asking for more photos than
            // there are targets could never be finished. Clamping here means no stored
            // config can strand someone in front of an alarm they cannot dismiss.
            photos = photos.coerceIn(PHOTOS.first, minOf(PHOTOS.last, usable.size)),
        )
    }

    companion object {
        val PHOTOS = 1..5
    }
}

/** Match every pair on a face-down board. */
@Serializable
@SerialName("memory")
data class MemoryChallenge(
    override val id: String = newChallengeId(),
    val pairs: Int = 4,
    override val difficulty: Difficulty = Difficulty.DEFAULT,
) : ChallengeConfig {
    override val kind: ChallengeKind get() = ChallengeKind.MEMORY

    override fun normalized(): MemoryChallenge = copy(pairs = pairs.coerceIn(PAIRS))

    companion object {
        val PAIRS = 3..10
    }
}

/** Retype a randomly generated string, once per round. */
@Serializable
@SerialName("retype")
data class RetypeChallenge(
    override val id: String = newChallengeId(),
    val length: Int = 8,
    val rounds: Int = 1,
    override val difficulty: Difficulty = Difficulty.DEFAULT,
) : ChallengeConfig {
    override val kind: ChallengeKind get() = ChallengeKind.RETYPE

    override fun normalized(): RetypeChallenge = copy(
        length = length.coerceIn(LENGTH),
        rounds = rounds.coerceIn(ROUNDS),
    )

    companion object {
        val LENGTH = 4..30
        val ROUNDS = 1..5
    }
}

/** Repeat a flashed sequence of shapes, Simon style. */
@Serializable
@SerialName("sequence")
data class SequenceChallenge(
    override val id: String = newChallengeId(),
    val shapes: Int = 4,
    val length: Int = 5,
    override val difficulty: Difficulty = Difficulty.DEFAULT,
) : ChallengeConfig {
    override val kind: ChallengeKind get() = ChallengeKind.SEQUENCE

    override fun normalized(): SequenceChallenge = copy(
        shapes = shapes.coerceIn(SHAPES),
        length = length.coerceIn(LENGTH),
    )

    companion object {
        val SHAPES = 3..9
        val LENGTH = 3..20
    }
}

/** Returns a fresh config of [kind] with default settings, for the add-challenge flow. */
fun defaultChallengeOf(kind: ChallengeKind): ChallengeConfig = when (kind) {
    ChallengeKind.MATH -> MathChallenge()
    ChallengeKind.PHOTO -> PhotoChallenge()
    ChallengeKind.MEMORY -> MemoryChallenge()
    ChallengeKind.RETYPE -> RetypeChallenge()
    ChallengeKind.SEQUENCE -> SequenceChallenge()
}
