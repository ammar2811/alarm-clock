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

import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.challenges.MathChallenge
import com.android.deskclock.challenges.engine.MathProblem
import com.android.deskclock.challenges.engine.MathProblemGenerator

/** Solve a run of arithmetic problems to dismiss the alarm. */
class MathChallengeFragment : ChallengeFragment() {

    /** The generated problems and how far through them the user is. */
    private class State(val problems: List<MathProblem>) {
        var index = 0
        val typed = StringBuilder()
        val current: MathProblem get() = problems[index]
        val isComplete: Boolean get() = index >= problems.size
    }

    /**
     * Resolved through the runner rather than held per fragment, so a configuration change
     * does not hand the user a different set of problems part way through.
     */
    private val state: State by lazy {
        val math = config as MathChallenge
        runner.engine(engineKey) {
            State(MathProblemGenerator.sequence(math.equations, math.difficulty))
        }
    }

    private lateinit var expressionView: TextView
    private lateinit var answerView: TextView

    override val contentLayoutRes: Int get() = R.layout.challenge_content_math

    override val promptText: String
        get() {
            val math = config as MathChallenge
            val prompt = getString(R.string.challenge_math_prompt)
            if (math.equations == 1) return prompt
            val step = getString(R.string.challenge_step,
                    (state.index + 1).coerceAtMost(math.equations), math.equations)
            return "$prompt  $step"
        }

    override fun onContentCreated(view: View) {
        expressionView = view.findViewById(R.id.math_expression)
        answerView = view.findViewById(R.id.math_answer)
        buildKeypad(view.findViewById(R.id.math_keypad))
        render()
    }

    private fun render() {
        if (state.isComplete) {
            pass()
            return
        }
        expressionView.text = getString(R.string.challenge_math_expression,
                state.current.expression)
        answerView.text = state.typed.toString()
        promptView.text = promptText
    }

    private fun buildKeypad(keypad: GridLayout) {
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9",
                DELETE, "0", SUBMIT)
        val keyBackground = androidx.appcompat.R.attr.selectableItemBackgroundBorderless
        val textColor = ThemeUtils.resolveColor(requireContext(), com.google.android.material.R.attr.colorOnSurface)
        val padding = resources.getDimensionPixelSize(R.dimen.challenge_key_padding)

        for ((position, label) in labels.withIndex()) {
            val key = TextView(requireContext()).apply {
                text = when (label) {
                    DELETE -> "⌫"
                    SUBMIT -> "✓"
                    else -> label
                }
                contentDescription = when (label) {
                    DELETE -> getString(R.string.challenge_backspace_description)
                    SUBMIT -> getString(R.string.challenge_submit_description)
                    else -> label
                }
                gravity = Gravity.CENTER
                textSize = 28f
                setTextColor(textColor)
                background = ThemeUtils.resolveDrawable(requireContext(), keyBackground)
                setPadding(0, padding, 0, padding)
                setOnClickListener { onKey(label) }
            }

            keypad.addView(key, GridLayout.LayoutParams(
                    GridLayout.spec(position / 3, 1f),
                    GridLayout.spec(position % 3, 1f)).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
            })
        }
    }

    private fun onKey(label: String) {
        when (label) {
            DELETE -> if (state.typed.isNotEmpty()) {
                state.typed.deleteCharAt(state.typed.length - 1)
            }
            SUBMIT -> {
                submit()
                return
            }
            // Capped so a repeatedly tapped key cannot build an absurd string.
            else -> if (state.typed.length < MAX_ANSWER_DIGITS) state.typed.append(label)
        }
        clearMessage()
        answerView.text = state.typed.toString()
    }

    private fun submit() {
        val answer = state.typed.toString().toIntOrNull()
        if (answer == null || answer != state.current.answer) {
            // Clear the attempt rather than leaving it to be edited: otherwise a wrong
            // answer can be walked to the right one by nudging a single digit.
            state.typed.clear()
            answerView.text = ""
            showMessage(getString(R.string.challenge_math_wrong))
            return
        }

        state.index++
        state.typed.clear()
        clearMessage()
        render()
    }

    companion object {
        private const val DELETE = "delete"
        private const val SUBMIT = "submit"
        private const val MAX_ANSWER_DIGITS = 7
    }
}
