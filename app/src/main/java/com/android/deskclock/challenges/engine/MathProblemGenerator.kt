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
import kotlin.random.Random

/** One arithmetic problem: what to show, and the only accepted answer. */
data class MathProblem(val expression: String, val answer: Int)

/**
 * Builds the arithmetic problems for the math challenge.
 *
 * Expressions are assembled from numbers and operators and then evaluated, rather than
 * formatted from a precomputed answer, so the displayed expression and the accepted answer
 * cannot drift apart. Answers are always non-negative, which keeps the answer pad to digits
 * with no sign key.
 */
object MathProblemGenerator {

    private enum class Op(val symbol: String) {
        PLUS("+"),
        MINUS("−"),
        TIMES("×"),
    }

    /** Attempts before giving up on a non-negative answer and using addition only. */
    private const val MAX_ATTEMPTS = 24

    fun next(difficulty: Difficulty, random: Random = Random.Default): MathProblem {
        val spec = ChallengeTuning.math(difficulty)

        repeat(MAX_ATTEMPTS) {
            val problem = build(spec, random)
            // Subtraction can land below zero, which would need a sign key on the pad.
            if (problem.answer >= 0) return problem
        }
        return buildAdditionOnly(spec, random)
    }

    /** Generates [count] problems, as one challenge presents them in order. */
    fun sequence(count: Int, difficulty: Difficulty,
                 random: Random = Random.Default): List<MathProblem> =
        List(count) { next(difficulty, random) }

    private fun build(spec: ChallengeTuning.Math, random: Random): MathProblem {
        val ops = mutableListOf<Op>()
        repeat(spec.terms - 1) {
            ops += if (spec.allowMultiply) {
                Op.entries.random(random)
            } else {
                listOf(Op.PLUS, Op.MINUS).random(random)
            }
        }
        // At most one product per expression: two multiplications at these operand sizes
        // stop being mental arithmetic.
        var seenTimes = false
        for (i in ops.indices) {
            if (ops[i] == Op.TIMES) {
                if (seenTimes) ops[i] = Op.PLUS else seenTimes = true
            }
        }

        val numbers = MutableList(spec.terms) { index ->
            // A factor of a product is drawn from the smaller range so the product stays
            // reasonable, while plain addends can use the full range.
            val isFactor = (index > 0 && ops[index - 1] == Op.TIMES) ||
                    (index < ops.size && ops[index] == Op.TIMES)
            if (isFactor) random.nextInt(spec.multiplicandRange)
            else random.nextInt(spec.operandRange)
        }

        return MathProblem(format(numbers, ops), evaluate(numbers, ops))
    }

    private fun buildAdditionOnly(spec: ChallengeTuning.Math, random: Random): MathProblem {
        val ops = List(spec.terms - 1) { Op.PLUS }
        val numbers = List(spec.terms) { random.nextInt(spec.operandRange) }
        return MathProblem(format(numbers, ops), evaluate(numbers, ops))
    }

    private fun format(numbers: List<Int>, ops: List<Op>): String {
        val text = StringBuilder(numbers[0].toString())
        for ((i, op) in ops.withIndex()) {
            text.append(' ').append(op.symbol).append(' ').append(numbers[i + 1])
        }
        return text.toString()
    }

    /** Evaluates with normal precedence: products first, then left to right. */
    private fun evaluate(numbers: List<Int>, ops: List<Op>): Int {
        val nums = numbers.toMutableList()
        val operators = ops.toMutableList()

        var i = 0
        while (i < operators.size) {
            if (operators[i] == Op.TIMES) {
                nums[i] = nums[i] * nums[i + 1]
                nums.removeAt(i + 1)
                operators.removeAt(i)
            } else {
                i++
            }
        }

        var result = nums[0]
        for ((j, op) in operators.withIndex()) {
            result = if (op == Op.PLUS) result + nums[j + 1] else result - nums[j + 1]
        }
        return result
    }

    private fun Random.nextInt(range: IntRange): Int = nextInt(range.first, range.last + 1)
}
