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

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.getSystemService
import com.android.deskclock.R
import com.android.deskclock.challenges.RetypeChallenge
import com.android.deskclock.challenges.engine.RetypeGenerator

/** Retype a generated string, once per round, to dismiss the alarm. */
class RetypeChallengeFragment : ChallengeFragment() {

    /** The generated targets and how far through the rounds the user is. */
    private class State(val targets: List<String>) {
        var index = 0
        val current: String get() = targets[index]
        val isComplete: Boolean get() = index >= targets.size
    }

    private val state: State by lazy {
        val retype = config as RetypeChallenge
        runner.engine(engineKey) {
            State(RetypeGenerator.sequence(retype.rounds, retype.length, retype.difficulty))
        }
    }

    private lateinit var targetView: TextView
    private lateinit var inputView: EditText

    override val contentLayoutRes: Int get() = R.layout.challenge_content_retype

    override val promptText: String
        get() {
            val retype = config as RetypeChallenge
            val prompt = getString(R.string.challenge_retype_prompt)
            if (retype.rounds == 1) return prompt
            val step = getString(R.string.challenge_step,
                    (state.index + 1).coerceAtMost(retype.rounds), retype.rounds)
            return "$prompt  $step"
        }

    override fun onContentCreated(view: View) {
        targetView = view.findViewById(R.id.retype_target)
        inputView = view.findViewById(R.id.retype_input)

        inputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                submit()
                true
            } else {
                false
            }
        }
        view.findViewById<Button>(R.id.retype_submit).setOnClickListener { submit() }

        render()
    }

    override fun onResume() {
        super.onResume()
        // The ring screen sets stateAlwaysHidden, so the keyboard has to be asked for.
        inputView.requestFocus()
        inputView.post {
            if (!isAdded) return@post
            requireContext().getSystemService<InputMethodManager>()
                    ?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun render() {
        if (state.isComplete) {
            pass()
            return
        }
        targetView.text = state.current
        inputView.setText("")
        promptView.text = promptText
    }

    private fun submit() {
        val retype = config as RetypeChallenge
        val typed = inputView.text.toString()

        if (!RetypeGenerator.matches(typed, state.current, retype.difficulty)) {
            inputView.setText("")
            showMessage(getString(R.string.challenge_retype_wrong))
            return
        }

        state.index++
        clearMessage()
        render()
    }
}
