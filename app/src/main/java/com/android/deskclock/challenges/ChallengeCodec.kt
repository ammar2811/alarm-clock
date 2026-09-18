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

import kotlinx.serialization.json.Json

/**
 * Converts an alarm's challenge list to and from the single JSON text column it is stored
 * in, on both the alarm template and the alarm instance.
 *
 * Compatibility comes from the `type` discriminator plus a default for every field: a row
 * written by an older build gains new fields at their defaults, and a row written by a newer
 * build has unknown fields ignored. There is no separate schema version, because an
 * incompatible change would need a real database migration anyway.
 *
 * [decode] never throws. A malformed row yields an empty list, so a bad value can cost an
 * alarm its challenges but can never stop the alarm from working.
 */
object ChallengeCodec {

    /** The value stored for an alarm with no challenges, and the column's SQL default. */
    const val EMPTY = "[]"

    private val json = Json {
        // Tolerate fields written by a newer build.
        ignoreUnknownKeys = true
        // Write every field so a stored row is fully self describing.
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(challenges: List<ChallengeConfig>): String {
        if (challenges.isEmpty()) return EMPTY
        return json.encodeToString(challenges)
    }

    fun decode(raw: String?): List<ChallengeConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ChallengeConfig>>(raw).map { it.normalized() }
        } catch (e: Exception) {
            // Covers SerializationException for malformed or unknown-type payloads and
            // IllegalArgumentException for structurally wrong ones. Either way the alarm
            // must still ring, so fall back to no challenges.
            emptyList()
        }
    }

    /** True when [raw] names at least one challenge, without fully decoding it. */
    fun hasChallenges(raw: String?): Boolean = decode(raw).isNotEmpty()
}
