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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The challenge list is persisted as JSON in a single column, so this codec is the only
 * thing standing between a bad row and an alarm that misbehaves.
 */
class ChallengeCodecTest {

    @Test
    fun roundTrip_preservesEveryType() {
        val original = listOf(
            MathChallenge(id = "a", equations = 5, difficulty = Difficulty.HARD),
            PhotoChallenge(id = "b", targets = listOf("sink", "cup"), photos = 2,
                    difficulty = Difficulty.EASY),
            MemoryChallenge(id = "c", pairs = 7, difficulty = Difficulty.EXPERT),
            RetypeChallenge(id = "d", length = 20, rounds = 3, difficulty = Difficulty.MEDIUM),
            SequenceChallenge(id = "e", shapes = 6, length = 9, difficulty = Difficulty.HARD),
        )

        assertEquals(original, ChallengeCodec.decode(ChallengeCodec.encode(original)))
    }

    @Test
    fun roundTrip_preservesOrder() {
        // The list is ordered: challenges run back to back in this sequence at ring time.
        val original = listOf(
            SequenceChallenge(id = "1"),
            MathChallenge(id = "2"),
            MemoryChallenge(id = "3"),
        )

        val ids = ChallengeCodec.decode(ChallengeCodec.encode(original)).map { it.id }
        assertEquals(listOf("1", "2", "3"), ids)
    }

    @Test
    fun emptyList_encodesToColumnDefault() {
        assertEquals(ChallengeCodec.EMPTY, ChallengeCodec.encode(emptyList()))
    }

    @Test
    fun columnDefault_decodesToEmptyList() {
        assertEquals(emptyList<ChallengeConfig>(), ChallengeCodec.decode(ChallengeCodec.EMPTY))
    }

    @Test
    fun unparseableValues_decodeToEmptyListRatherThanThrowing() {
        // An alarm must still ring even if its challenge column is garbage.
        val bad = listOf(
            null,
            "",
            "   ",
            "not json at all",
            "{}",
            "[{\"type\":\"math\"",
            "[{\"type\":\"squats\",\"id\":\"x\"}]",
            "[{\"id\":\"no type at all\"}]",
        )

        for (raw in bad) {
            assertEquals("expected no challenges for: $raw",
                    emptyList<ChallengeConfig>(), ChallengeCodec.decode(raw))
        }
    }

    @Test
    fun unknownFields_areIgnored() {
        // Forward compatibility: a row written by a newer build still decodes here.
        val raw = """[{"type":"math","id":"a","equations":4,"difficulty":"hard","future":true}]"""

        assertEquals(listOf(MathChallenge(id = "a", equations = 4, difficulty = Difficulty.HARD)),
                ChallengeCodec.decode(raw))
    }

    @Test
    fun missingFields_fallBackToDefaults() {
        // Backward compatibility: a row written before a field existed still decodes.
        val decoded = ChallengeCodec.decode("""[{"type":"retype","id":"a"}]""")

        assertEquals(listOf(RetypeChallenge(id = "a")), decoded)
    }

    @Test
    fun photoCount_cannotExceedTheNumberOfTargets() {
        // Every photo must be a different target, so two photos against one target could
        // never be finished. An alarm nobody can dismiss is worse than a weaker challenge.
        val decoded = ChallengeCodec.decode(
                """[{"type":"photo","id":"a","targets":["cup"],"photos":4}]""")

        assertEquals(1, (decoded[0] as PhotoChallenge).photos)
    }

    @Test
    fun photoCount_survivesWhenThereAreEnoughTargets() {
        val decoded = ChallengeCodec.decode(
                """[{"type":"photo","id":"a","targets":["cup","sink","clock"],"photos":3}]""")

        assertEquals(3, (decoded[0] as PhotoChallenge).photos)
    }

    @Test
    fun outOfRangeNumbers_areClampedOnDecode() {
        val raw = """[
            {"type":"math","id":"a","equations":99},
            {"type":"memory","id":"b","pairs":0},
            {"type":"retype","id":"c","length":1000,"rounds":0},
            {"type":"sequence","id":"d","shapes":1,"length":99},
            {"type":"photo","id":"e","photos":42,
             "targets":["cup","sink","chair","book","clock","bed"]}
        ]"""

        val decoded = ChallengeCodec.decode(raw)

        assertEquals(MathChallenge.EQUATIONS.last, (decoded[0] as MathChallenge).equations)
        assertEquals(MemoryChallenge.PAIRS.first, (decoded[1] as MemoryChallenge).pairs)
        val retype = decoded[2] as RetypeChallenge
        assertEquals(RetypeChallenge.LENGTH.last, retype.length)
        assertEquals(RetypeChallenge.ROUNDS.first, retype.rounds)
        val sequence = decoded[3] as SequenceChallenge
        assertEquals(SequenceChallenge.SHAPES.first, sequence.shapes)
        assertEquals(SequenceChallenge.LENGTH.last, sequence.length)
        assertEquals(PhotoChallenge.PHOTOS.last, (decoded[4] as PhotoChallenge).photos)
    }

    @Test
    fun photoTargets_dropLabelsTheModelCannotDetect() {
        val raw = """[{"type":"photo","id":"a","targets":["sink","unicorn","cup"]}]"""

        val decoded = ChallengeCodec.decode(raw).single() as PhotoChallenge

        assertEquals(listOf("sink", "cup"), decoded.targets)
    }

    @Test
    fun photoTargets_neverEndUpEmpty() {
        // An empty target set would be impossible to satisfy, trapping the user.
        val raw = """[{"type":"photo","id":"a","targets":["unicorn"]}]"""

        val decoded = ChallengeCodec.decode(raw).single() as PhotoChallenge

        assertEquals(listOf(CocoLabels.DEFAULT_TARGET), decoded.targets)
    }

    @Test
    fun photoTargets_areDeduplicated() {
        val raw = """[{"type":"photo","id":"a","targets":["cup","cup","sink"]}]"""

        val decoded = ChallengeCodec.decode(raw).single() as PhotoChallenge

        assertEquals(listOf("cup", "sink"), decoded.targets)
    }

    @Test
    fun hasChallenges_reflectsWhetherAnyAreConfigured() {
        assertFalse(ChallengeCodec.hasChallenges(null))
        assertFalse(ChallengeCodec.hasChallenges(ChallengeCodec.EMPTY))
        assertFalse(ChallengeCodec.hasChallenges("garbage"))
        assertTrue(ChallengeCodec.hasChallenges(ChallengeCodec.encode(listOf(MathChallenge()))))
    }

    @Test
    fun newChallengeId_isUnique() {
        val ids = (1..100).map { newChallengeId() }
        assertEquals(100, ids.distinct().size)
    }

    @Test
    fun defaultChallengeOf_coversEveryKind() {
        for (kind in ChallengeKind.entries) {
            assertEquals(kind, defaultChallengeOf(kind).kind)
        }
    }

    @Test
    fun defaultsAreInRange() {
        // Every default must survive normalization unchanged, or the picker would hand the
        // user a config that silently changes the moment it is saved and reloaded.
        for (kind in ChallengeKind.entries) {
            val default = defaultChallengeOf(kind)
            assertEquals(kind.toString(), default, default.normalized())
        }
    }
}
