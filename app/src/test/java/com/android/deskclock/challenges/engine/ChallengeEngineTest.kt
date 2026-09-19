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

package com.android.deskclock.challenges.engine

import com.android.deskclock.challenges.ChallengeTuning
import com.android.deskclock.challenges.Difficulty
import com.android.deskclock.challenges.MemoryChallenge
import com.android.deskclock.challenges.RetypeChallenge
import com.android.deskclock.challenges.SequenceChallenge
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules for every challenge type.
 *
 * Randomness is always seeded, so a failure here is reproducible rather than a flake. Where
 * a property must hold for every generated value, the test sweeps many seeds instead of
 * trusting one.
 */
class ChallengeEngineTest {

    private fun seeds() = (1..300).map { Random(it) }

    // ------------------------------------------------------------------ math

    @Test
    fun math_expressionAlwaysEvaluatesToItsStatedAnswer() {
        // The whole challenge is worthless if the shown expression and the accepted answer
        // disagree, so this re-evaluates the printed text independently.
        for (difficulty in Difficulty.entries) {
            for (random in seeds()) {
                val problem = MathProblemGenerator.next(difficulty, random)
                assertEquals("${problem.expression} at $difficulty",
                        evaluateIndependently(problem.expression), problem.answer)
            }
        }
    }

    @Test
    fun math_answersAreNeverNegative() {
        // The answer pad has digits only, with no sign key.
        for (difficulty in Difficulty.entries) {
            for (random in seeds()) {
                val problem = MathProblemGenerator.next(difficulty, random)
                assertTrue("${problem.expression} = ${problem.answer}", problem.answer >= 0)
            }
        }
    }

    @Test
    fun math_termCountFollowsDifficulty() {
        for (difficulty in Difficulty.entries) {
            val expected = ChallengeTuning.math(difficulty).terms
            for (random in seeds()) {
                val problem = MathProblemGenerator.next(difficulty, random)
                val terms = problem.expression.split(' ').filter { it.toIntOrNull() != null }
                assertEquals(problem.expression, expected, terms.size)
            }
        }
    }

    @Test
    fun math_easyDifficultyNeverMultiplies() {
        // Multiplication is what separates easy from the rest.
        for (random in seeds()) {
            val problem = MathProblemGenerator.next(Difficulty.EASY, random)
            assertFalse(problem.expression, problem.expression.contains('×'))
        }
    }

    @Test
    fun math_neverAsksForTwoProductsAtOnce() {
        for (difficulty in Difficulty.entries) {
            for (random in seeds()) {
                val problem = MathProblemGenerator.next(difficulty, random)
                assertTrue(problem.expression,
                        problem.expression.count { it == '×' } <= 1)
            }
        }
    }

    @Test
    fun math_sameSeedGivesSameProblems() {
        val first = MathProblemGenerator.sequence(5, Difficulty.HARD, Random(42))
        val second = MathProblemGenerator.sequence(5, Difficulty.HARD, Random(42))
        assertEquals(first, second)
    }

    @Test
    fun math_sequenceReturnsTheRequestedCount() {
        assertEquals(7, MathProblemGenerator.sequence(7, Difficulty.MEDIUM, Random(1)).size)
    }

    /** Evaluates "a op b [op c]" with normal precedence, independently of the generator. */
    private fun evaluateIndependently(expression: String): Int {
        val tokens = expression.split(' ')
        val numbers = tokens.filterIndexed { i, _ -> i % 2 == 0 }.map { it.toInt() }
        val ops = tokens.filterIndexed { i, _ -> i % 2 == 1 }

        val nums = numbers.toMutableList()
        val operators = ops.toMutableList()
        var i = 0
        while (i < operators.size) {
            if (operators[i] == "×") {
                nums[i] = nums[i] * nums[i + 1]
                nums.removeAt(i + 1)
                operators.removeAt(i)
            } else {
                i++
            }
        }
        var result = nums[0]
        for ((j, op) in operators.withIndex()) {
            result = if (op == "+") result + nums[j + 1] else result - nums[j + 1]
        }
        return result
    }

    // ------------------------------------------------------------------ retype

    @Test
    fun retype_targetHasTheRequestedLength() {
        for (length in RetypeChallenge.LENGTH) {
            for (difficulty in Difficulty.entries) {
                val target = RetypeGenerator.next(length, difficulty, Random(length))
                assertEquals(length, target.length)
            }
        }
    }

    @Test
    fun retype_targetOnlyUsesItsDifficultyAlphabet() {
        for (difficulty in Difficulty.entries) {
            val alphabet = ChallengeTuning.retype(difficulty).alphabet.toSet()
            for (random in seeds()) {
                val target = RetypeGenerator.next(12, difficulty, random)
                assertTrue("$target at $difficulty", target.all { it in alphabet })
            }
        }
    }

    @Test
    fun retype_easyAndMediumAvoidAmbiguousGlyphs() {
        // Telling l from 1 and O from 0 at 6am is a reading test, not a wake-up test.
        val ambiguous = setOf('l', '1', 'I', 'O', '0')
        for (difficulty in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            val alphabet = ChallengeTuning.retype(difficulty).alphabet
            assertTrue("$difficulty alphabet: $alphabet",
                    alphabet.none { it in ambiguous })
        }
    }

    @Test
    fun retype_easyIgnoresCaseAndHarderLevelsDoNot() {
        val target = "abcd"
        assertTrue(RetypeGenerator.matches("ABCD", target, Difficulty.EASY))
        assertFalse(RetypeGenerator.matches("ABCD", target, Difficulty.MEDIUM))
        assertFalse(RetypeGenerator.matches("ABCD", target, Difficulty.HARD))
        assertFalse(RetypeGenerator.matches("ABCD", target, Difficulty.EXPERT))
    }

    @Test
    fun retype_toleratesSurroundingWhitespace() {
        // Software keyboards readily append a space, which is not the mistake under test.
        assertTrue(RetypeGenerator.matches("  abcd ", "abcd", Difficulty.MEDIUM))
    }

    @Test
    fun retype_rejectsWrongText() {
        assertFalse(RetypeGenerator.matches("abce", "abcd", Difficulty.EASY))
        assertFalse(RetypeGenerator.matches("abc", "abcd", Difficulty.EASY))
        assertFalse(RetypeGenerator.matches("", "abcd", Difficulty.EASY))
    }

    @Test
    fun retype_sequenceReturnsOneTargetPerRound() {
        assertEquals(3, RetypeGenerator.sequence(3, 8, Difficulty.EASY, Random(1)).size)
    }

    // ------------------------------------------------------------------ memory

    @Test
    fun memory_boardHasTwoCardsPerPair() {
        for (pairs in MemoryChallenge.PAIRS) {
            val board = MemoryBoard(pairs, Random(pairs))
            assertEquals(pairs * 2, board.size)
            val counts = board.cards.groupingBy { it.symbol }.eachCount()
            assertEquals(pairs, counts.size)
            assertTrue("every symbol appears twice", counts.values.all { it == 2 })
        }
    }

    @Test
    fun memory_startsFaceDown() {
        val board = MemoryBoard(4, Random(1))
        assertTrue(board.cards.none { it.faceUp })
        assertTrue(board.cards.none { it.matched })
        assertFalse(board.isSolved)
    }

    @Test
    fun memory_matchingPairStaysFaceUp() {
        val board = MemoryBoard(4, Random(7))
        val (first, second) = board.positionsOfSomePair()

        assertEquals(MemoryBoard.Tap.Revealed, board.tap(first))
        assertEquals(MemoryBoard.Tap.Match, board.tap(second))

        assertTrue(board.cards[first].matched)
        assertTrue(board.cards[second].matched)
    }

    @Test
    fun memory_mismatchedPairFlipsBackOnRequest() {
        val board = MemoryBoard(4, Random(7))
        val (a, b) = board.positionsOfDifferentSymbols()

        board.tap(a)
        val tap = board.tap(b)

        assertEquals(MemoryBoard.Tap.Mismatch(a, b), tap)
        assertTrue("both stay visible during the peek",
                board.cards[a].faceUp && board.cards[b].faceUp)

        board.flipBackMismatch()
        assertFalse(board.cards[a].faceUp)
        assertFalse(board.cards[b].faceUp)
    }

    @Test
    fun memory_ignoresTapsWhileAMismatchIsShowing() {
        // A fast tapper must not be able to reveal a third card mid peek.
        val board = MemoryBoard(4, Random(7))
        val (a, b) = board.positionsOfDifferentSymbols()
        board.tap(a)
        board.tap(b)

        val other = board.cards.indices.first { it != a && it != b }
        assertEquals(MemoryBoard.Tap.Ignored, board.tap(other))
        assertFalse(board.cards[other].faceUp)
    }

    @Test
    fun memory_ignoresRetappingAndOutOfRangeTaps() {
        val board = MemoryBoard(3, Random(2))
        board.tap(0)
        assertEquals(MemoryBoard.Tap.Ignored, board.tap(0))
        assertEquals(MemoryBoard.Tap.Ignored, board.tap(-1))
        assertEquals(MemoryBoard.Tap.Ignored, board.tap(board.size))
    }

    @Test
    fun memory_lastPairReportsSolved() {
        val board = MemoryBoard(3, Random(5))
        var solved = false

        // Play perfectly, pairing up each symbol in turn.
        while (!board.isSolved) {
            val (first, second) = board.positionsOfSomePair()
            board.tap(first)
            solved = board.tap(second) == MemoryBoard.Tap.Solved
        }

        assertTrue("the final match should report Solved", solved)
        assertTrue(board.isSolved)
        assertTrue(board.cards.all { it.matched })
    }

    @Test
    fun memory_previewRevealsThenHidesUnmatchedCards() {
        val board = MemoryBoard(3, Random(9))
        board.revealAll()
        assertTrue(board.cards.all { it.faceUp })

        board.hideUnmatched()
        assertTrue(board.cards.none { it.faceUp })
    }

    /** Positions of two unmatched cards sharing a symbol. */
    private fun MemoryBoard.positionsOfSomePair(): Pair<Int, Int> {
        val cards = this.cards
        val symbol = cards.withIndex().first { !it.value.matched }.value.symbol
        val positions = cards.withIndex()
                .filter { it.value.symbol == symbol && !it.value.matched }
                .map { it.index }
        return positions[0] to positions[1]
    }

    /** Positions of two cards with different symbols. */
    private fun MemoryBoard.positionsOfDifferentSymbols(): Pair<Int, Int> {
        val cards = this.cards
        val first = 0
        val second = cards.indices.first { cards[it].symbol != cards[first].symbol }
        return first to second
    }

    // ------------------------------------------------------------------ sequence

    @Test
    fun sequence_hasTheRequestedLengthAndShapeRange() {
        for (shapes in SequenceChallenge.SHAPES) {
            val game = SequenceGame(shapes, length = 8, random = Random(shapes))
            assertEquals(8, game.sequence.size)
            assertTrue(game.sequence.all { it in 0 until shapes })
        }
    }

    @Test
    fun sequence_correctTapsAdvanceToCompletion() {
        val game = SequenceGame(4, length = 3, random = Random(3))
        val expected = game.sequence

        assertEquals(MemorylessCorrect(1, 3), game.tap(expected[0]).asCorrect())
        assertEquals(MemorylessCorrect(2, 3), game.tap(expected[1]).asCorrect())
        assertEquals(SequenceGame.Tap.Complete, game.tap(expected[2]))
        assertTrue(game.isComplete)
    }

    @Test
    fun sequence_wrongTapResetsProgressButKeepsTheSequence() {
        val game = SequenceGame(4, length = 4, random = Random(11))
        val expected = game.sequence.toList()

        game.tap(expected[0])
        val wrong = (0 until 4).first { it != expected[1] }
        assertEquals(SequenceGame.Tap.Wrong, game.tap(wrong))

        assertEquals("progress resets", 0, game.matched)
        assertEquals("the sequence itself is unchanged", expected, game.sequence)

        // And it can be entered again from the start.
        for (shape in expected.dropLast(1)) game.tap(shape)
        assertEquals(SequenceGame.Tap.Complete, game.tap(expected.last()))
    }

    @Test
    fun sequence_restartEntryClearsProgressOnly() {
        val game = SequenceGame(5, length = 4, random = Random(13))
        val expected = game.sequence.toList()
        game.tap(expected[0])
        game.tap(expected[1])
        assertEquals(2, game.matched)

        game.restartEntry()

        assertEquals(0, game.matched)
        assertEquals(expected, game.sequence)
    }

    @Test
    fun sequence_playbackGetsFasterWithDifficulty() {
        val timings = Difficulty.entries.map { ChallengeTuning.sequence(it).litMillis }
        assertEquals(timings.sortedDescending(), timings)
        assertNotEquals(timings.first(), timings.last())
    }

    private data class MemorylessCorrect(val matched: Int, val total: Int)

    private fun SequenceGame.Tap.asCorrect(): MemorylessCorrect {
        val correct = this as SequenceGame.Tap.Correct
        return MemorylessCorrect(correct.matched, correct.total)
    }

    // ------------------------------------------------------------------ photo

    @Test
    fun photo_needsEnoughConsecutiveMatchingFrames() {
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.HARD)
        val required = ChallengeTuning.photo(Difficulty.HARD).requiredFrames
        val frame = listOf(Detection("cup", 0.9f))

        repeat(required - 1) {
            assertFalse(matcher.onFrame(frame))
        }
        assertTrue("should pass on frame $required", matcher.onFrame(frame))
    }

    @Test
    fun photo_namesWhichTargetPassed() {
        // The fragment needs the label so the next photo can demand a different one.
        val matcher = PhotoMatcher(listOf("cup", "sink"), Difficulty.EASY)

        assertTrue(matcher.onFrame(listOf(Detection("sink", 0.9f))))
        assertEquals("sink", matcher.matchedTarget)
    }

    @Test
    fun photo_streakIsKeptPerTarget() {
        // Glancing between two targets must not add up to one streak: a streak has to mean
        // a single object held steadily in view, or two half sightings would pass a photo.
        val matcher = PhotoMatcher(listOf("cup", "sink"), Difficulty.HARD)

        repeat(ChallengeTuning.photo(Difficulty.HARD).requiredFrames * 2) { i ->
            val label = if (i % 2 == 0) "cup" else "sink"
            assertFalse(matcher.onFrame(listOf(Detection(label, 0.9f))))
        }
        assertNull(matcher.matchedTarget)
    }

    @Test
    fun photo_matchedTargetIsNullUntilSatisfied() {
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.EXPERT)

        matcher.onFrame(listOf(Detection("cup", 0.95f)))
        assertNull(matcher.matchedTarget)
    }

    @Test
    fun photo_streakResetsOnANonMatchingFrame() {
        // This is what stops one lucky frame from dismissing an alarm.
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.EXPERT)
        val hit = listOf(Detection("cup", 0.95f))

        matcher.onFrame(hit)
        matcher.onFrame(hit)
        assertEquals(2, matcher.consecutiveMatches)

        matcher.onFrame(listOf(Detection("chair", 0.99f)))
        assertEquals(0, matcher.consecutiveMatches)
        assertFalse(matcher.isSatisfied)
    }

    @Test
    fun photo_ignoresDetectionsBelowTheConfidenceThreshold() {
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.HARD)
        val threshold = ChallengeTuning.photo(Difficulty.HARD).scoreThreshold

        repeat(10) { assertFalse(matcher.onFrame(
                listOf(Detection("cup", threshold - 0.01f)))) }
        assertEquals(0, matcher.consecutiveMatches)
    }

    @Test
    fun photo_matchesAnyOfSeveralTargets() {
        val matcher = PhotoMatcher(listOf("cup", "sink"), Difficulty.EASY)
        assertTrue(matcher.onFrame(listOf(Detection("sink", 0.8f))))
    }

    @Test
    fun photo_labelComparisonIgnoresCase() {
        // The detector's reported casing is not something to depend on.
        val matcher = PhotoMatcher(listOf("teddy bear"), Difficulty.EASY)
        assertTrue(matcher.onFrame(listOf(Detection("Teddy Bear", 0.8f))))
    }

    @Test
    fun photo_emptyFramesDoNotCount() {
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.EASY)
        assertFalse(matcher.onFrame(emptyList()))
        assertEquals(0, matcher.consecutiveMatches)
    }

    @Test
    fun photo_resetClearsTheStreak() {
        val matcher = PhotoMatcher(listOf("cup"), Difficulty.EXPERT)
        matcher.onFrame(listOf(Detection("cup", 0.9f)))
        matcher.reset()
        assertEquals(0, matcher.consecutiveMatches)
    }

    @Test
    fun photo_getsStricterWithDifficulty() {
        val thresholds = Difficulty.entries.map { ChallengeTuning.photo(it).scoreThreshold }
        val frames = Difficulty.entries.map { ChallengeTuning.photo(it).requiredFrames }
        assertEquals(thresholds.sorted(), thresholds)
        assertEquals(frames.sorted(), frames)
    }
}
