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

package com.android.deskclock.challenges.ui

import androidx.lifecycle.ViewModel
import com.android.deskclock.challenges.ChallengeConfig

/**
 * Walks an alarm's challenges in order and holds each one's engine.
 *
 * This lives in a ViewModel so a configuration change does not restart a half-finished
 * memory board or reshuffle a sequence the user has already watched. Progress is kept in
 * memory only and never persisted: a process death must not leave an alarm pre-authorised
 * for dismissal.
 */
class ChallengeRunnerViewModel : ViewModel() {

    private var challenges: List<ChallengeConfig> = emptyList()
    private val engines = mutableMapOf<String, Any>()

    /** Index of the challenge currently being shown. */
    var index = 0
        private set

    var isStarted = false
        private set

    val total: Int get() = challenges.size

    val current: ChallengeConfig? get() = challenges.getOrNull(index)

    /** Human position of the current challenge, counting from one. */
    val position: Int get() = index + 1

    /** Begins a run, or does nothing if one is already under way. */
    fun startIfNeeded(challenges: List<ChallengeConfig>) {
        if (isStarted) return
        this.challenges = challenges
        index = 0
        isStarted = true
    }

    /**
     * Moves to the next challenge.
     *
     * @return true if another challenge is now current, false when they are all complete.
     */
    fun advance(): Boolean {
        if (index < challenges.size) index++
        return index < challenges.size
    }

    /**
     * Returns the engine for [key], creating it once and reusing it afterwards so that
     * revisiting a challenge does not regenerate its problem.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> engine(key: String, create: () -> T): T =
        engines.getOrPut(key) { create() } as T

    /** Abandons the run so the next dismiss attempt starts the challenges over. */
    fun reset() {
        challenges = emptyList()
        engines.clear()
        index = 0
        isStarted = false
    }
}
