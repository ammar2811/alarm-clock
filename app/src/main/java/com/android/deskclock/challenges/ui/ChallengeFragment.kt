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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.challenges.ChallengeConfig

/**
 * Shared behaviour for the challenge screens: the common chrome, access to the host, and the
 * run-scoped engine store.
 *
 * Subclasses supply [contentLayoutRes] and their own logic. Everything a challenge needs to
 * decide pass or fail lives in a plain engine class, so these fragments only draw and
 * forward taps.
 */
abstract class ChallengeFragment : Fragment() {

    /** The challenge-specific content, inflated into the shell. */
    @get:LayoutRes
    protected abstract val contentLayoutRes: Int

    /** One-line instruction shown above the content. */
    protected abstract val promptText: String

    protected lateinit var messageView: TextView
        private set

    protected lateinit var promptView: TextView
        private set

    private val host: ChallengeHost
        get() = requireActivity() as ChallengeHost

    /**
     * Engines are stored per run rather than per fragment, so a configuration change does
     * not regenerate the problem the user is already looking at.
     */
    protected val runner: ChallengeRunnerViewModel
        get() = ViewModelProvider(requireActivity())[ChallengeRunnerViewModel::class.java]

    /** The config this fragment is rendering. */
    protected val config: ChallengeConfig
        get() = requireNotNull(runner.current) { "no current challenge" }

    /** Distinguishes this challenge's engine from the others in the same run. */
    protected val engineKey: String get() = config.id

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val shell = inflater.inflate(R.layout.challenge_shell, container, false)

        val clock = shell.findViewById<TextClock>(R.id.challenge_clock)
        Utils.setTimeFormat(clock, false)

        val progress = shell.findViewById<TextView>(R.id.challenge_progress)
        val progressText = host.progressText
        if (progressText == null) {
            progress.visibility = View.GONE
        } else {
            progress.visibility = View.VISIBLE
            progress.text = progressText
        }

        promptView = shell.findViewById(R.id.challenge_prompt)
        promptView.text = promptText
        messageView = shell.findViewById(R.id.challenge_message)

        val content = shell.findViewById<FrameLayout>(R.id.challenge_content)
        val contentView = inflater.inflate(contentLayoutRes, content, true)
        onContentCreated(contentView)

        return shell
    }

    /** Called once the challenge's own content is inflated. */
    protected abstract fun onContentCreated(view: View)

    /** Reports that this challenge is complete. */
    protected fun pass() {
        host.onChallengePassed()
    }

    /** Reports that this challenge cannot be completed at all, and why. */
    protected fun unavailable(reason: String) {
        host.onChallengeUnavailable(reason)
    }

    /** Shows a transient message, for example after a wrong answer. */
    protected fun showMessage(text: String?) {
        messageView.text = text ?: ""
    }

    protected fun clearMessage() {
        messageView.text = ""
    }
}
