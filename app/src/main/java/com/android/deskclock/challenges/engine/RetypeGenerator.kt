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

import com.android.deskclock.challenges.ChallengeTuning
import com.android.deskclock.challenges.Difficulty
import kotlin.random.Random

/**
 * Builds and checks the target strings for the retype challenge.
 *
 * Case sensitivity is a property of difficulty rather than of the comparison site, so that
 * an easy challenge cannot be failed by an autocapitalising keyboard.
 */
object RetypeGenerator {

    fun next(length: Int, difficulty: Difficulty, random: Random = Random.Default): String {
        val alphabet = ChallengeTuning.retype(difficulty).alphabet
        return String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })
    }

    /** Generates one target per round, as one challenge presents them in order. */
    fun sequence(rounds: Int, length: Int, difficulty: Difficulty,
                 random: Random = Random.Default): List<String> =
        List(rounds) { next(length, difficulty, random) }

    /** True when [typed] satisfies [target] at this [difficulty]. */
    fun matches(typed: String, target: String, difficulty: Difficulty): Boolean {
        val caseSensitive = ChallengeTuning.retype(difficulty).caseSensitive
        // Trimmed because a software keyboard readily appends a trailing space, which is
        // not the mistake the challenge is testing for.
        return typed.trim().equals(target, ignoreCase = !caseSensitive)
    }
}
