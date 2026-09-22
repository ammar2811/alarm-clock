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
import com.android.deskclock.challenges.CocoLabels
import com.android.deskclock.challenges.Difficulty

/** One thing the detector reported in a frame. */
data class Detection(val label: String, val score: Float)

/**
 * Decides when the camera has actually seen what the photo challenge asked for.
 *
 * A single frame is not enough evidence. Object detectors emit occasional high-confidence
 * nonsense while the camera pans around a dark room, so a match has to hold for several
 * consecutive frames before it counts. The streak resets the moment a frame does not match,
 * which is what stops a lucky frame from dismissing an alarm.
 *
 * This class holds no camera or model types, so the matching rules are testable without a
 * device.
 */
class PhotoMatcher(
    private val targets: Collection<String>,
    difficulty: Difficulty,
) {
    private val tuning = ChallengeTuning.photo(difficulty)

    /**
     * Consecutive matching frames per target. Streaks are tracked separately so that
     * glancing between two different targets cannot add up to one streak; a streak has to
     * mean one object held steadily in view.
     */
    private val streaks = mutableMapOf<String, Int>()

    /** Consecutive matching frames for whichever target is closest to passing. */
    val consecutiveMatches: Int get() = streaks.values.maxOrNull() ?: 0

    val requiredFrames: Int get() = tuning.requiredFrames

    /** The target that has held for enough consecutive frames, or null if none has. */
    val matchedTarget: String?
        get() = targets.firstOrNull { (streaks[it] ?: 0) >= tuning.requiredFrames }

    /** True once enough consecutive frames have matched. */
    val isSatisfied: Boolean get() = matchedTarget != null

    /**
     * Feeds one frame's detections in.
     *
     * @return true once the challenge is satisfied; [matchedTarget] names which target did it.
     */
    fun onFrame(detections: List<Detection>): Boolean {
        val seen = detections.filter { it.score >= tuning.scoreThreshold }
        for (target in targets) {
            val hit = seen.any { CocoLabels.matches(it.label, listOf(target)) }
            streaks[target] = if (hit) (streaks[target] ?: 0) + 1 else 0
        }
        return isSatisfied
    }

    /** Forgets every streak, for example after the camera is restarted. */
    fun reset() {
        streaks.clear()
    }
}
