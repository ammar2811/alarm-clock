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

/**
 * What a challenge fragment can ask of the screen hosting it.
 *
 * Implemented by the ring screen during a real alarm and by the preview screen when trying a
 * challenge out from settings, so the fragments themselves do not know which they are in.
 */
interface ChallengeHost {

    /** The current challenge is complete. Move on, or dismiss if it was the last one. */
    fun onChallengePassed()

    /** The user backed out. Return to whatever was showing before the challenges. */
    fun onChallengeAbandoned()

    /**
     * Skips the current challenge, for the one case where it cannot be completed at all:
     * the photo challenge without camera permission. Never offered for anything the user
     * could actually finish, and never as a way out of a challenge they simply find hard.
     */
    fun onChallengeUnavailable(reason: String)

    /** Text for the progress line, for example "Challenge 2 of 3". Null hides it. */
    val progressText: String?
}
