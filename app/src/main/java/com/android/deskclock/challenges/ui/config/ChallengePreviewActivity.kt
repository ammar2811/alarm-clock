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

package com.android.deskclock.challenges.ui.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.android.deskclock.BaseActivity
import com.android.deskclock.R
import com.android.deskclock.challenges.ChallengeCodec
import com.android.deskclock.challenges.ChallengeConfig
import com.android.deskclock.challenges.ChallengeKind
import com.android.deskclock.challenges.ui.ChallengeFragment
import com.android.deskclock.challenges.ui.ChallengeHost
import com.android.deskclock.challenges.ui.ChallengeRunnerViewModel
import com.android.deskclock.challenges.ui.MathChallengeFragment
import com.android.deskclock.challenges.ui.MemoryChallengeFragment
import com.android.deskclock.challenges.ui.PhotoChallengeFragment
import com.android.deskclock.challenges.ui.RetypeChallengeFragment
import com.android.deskclock.challenges.ui.SequenceChallengeFragment

/**
 * Runs one challenge exactly as it will run at ring time, so a setting can be tried before
 * it is trusted with a morning.
 *
 * It hosts the same fragments as the ring screen through the same interface, so what is
 * previewed is what will actually fire. Nothing here can dismiss an alarm.
 */
class ChallengePreviewActivity : BaseActivity(), ChallengeHost {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenge_preview)

        val config = ChallengeCodec.decode(intent.getStringExtra(EXTRA_CONFIG)).firstOrNull()
        if (config == null) {
            finish()
            return
        }

        val runner = ViewModelProvider(this)[ChallengeRunnerViewModel::class.java]
        runner.startIfNeeded(listOf(config))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.preview_container, fragmentFor(config))
                    .commit()
        }
    }

    private fun fragmentFor(config: ChallengeConfig): ChallengeFragment = when (config.kind) {
        ChallengeKind.MATH -> MathChallengeFragment()
        ChallengeKind.MEMORY -> MemoryChallengeFragment()
        ChallengeKind.RETYPE -> RetypeChallengeFragment()
        ChallengeKind.SEQUENCE -> SequenceChallengeFragment()
        ChallengeKind.PHOTO -> PhotoChallengeFragment()
    }

    override fun onChallengePassed() {
        handler.post {
            if (isFinishing || isDestroyed) return@post
            Toast.makeText(this, R.string.challenge_preview_done, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onChallengeAbandoned() {
        handler.post { if (!isFinishing) finish() }
    }

    /** Never offered in a preview; see canSnooze. Closes the trial if it somehow arrives. */
    override fun onSnoozeRequested() {
        handler.post { if (!isFinishing) finish() }
    }

    override fun onChallengeUnavailable(reason: String) {
        handler.post {
            if (isFinishing || isDestroyed) return@post
            Toast.makeText(this, getString(R.string.challenge_skipped, reason),
                    Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /** Nothing is ringing, so there is nothing to snooze. */
    override val canSnooze: Boolean get() = false

    /** A preview runs one challenge, so there is no position to report. */
    override val progressText: String? get() = null

    companion object {
        private const val EXTRA_CONFIG = "com.android.deskclock.extra.CHALLENGE_CONFIG"

        fun createIntent(context: Context, config: ChallengeConfig): Intent =
            Intent(context, ChallengePreviewActivity::class.java)
                    .putExtra(EXTRA_CONFIG, ChallengeCodec.encode(listOf(config)))
    }
}
