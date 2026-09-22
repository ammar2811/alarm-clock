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

package com.android.deskclock.challenges

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How hard a challenge is. Each challenge type maps this onto its own parameters in one
 * place, rather than scattering difficulty checks through the engines.
 */
@Serializable
enum class Difficulty {
    @SerialName("easy") EASY,
    @SerialName("medium") MEDIUM,
    @SerialName("hard") HARD,
    @SerialName("expert") EXPERT;

    companion object {
        val DEFAULT = MEDIUM
    }
}
