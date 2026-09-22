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

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.challenges.ChallengeTuning
import com.android.deskclock.challenges.SequenceChallenge
import com.android.deskclock.challenges.engine.SequenceGame

/** Watch a sequence of shapes light up, then tap them back in order. */
class SequenceChallengeFragment : ChallengeFragment() {

    private val handler = Handler(Looper.getMainLooper())

    private val game: SequenceGame by lazy {
        val sequence = config as SequenceChallenge
        runner.engine(engineKey) { SequenceGame(sequence.shapes, sequence.length) }
    }

    private lateinit var shapeGrid: GridLayout
    private lateinit var progressRow: LinearLayout
    private lateinit var replayButton: Button
    private val shapeViews = mutableListOf<TextView>()
    private val dotViews = mutableListOf<TextView>()

    /** True while the sequence is playing, when taps must be ignored. */
    private var isPlaying = false

    override val contentLayoutRes: Int get() = R.layout.challenge_content_sequence

    override val promptText: String get() = getString(R.string.challenge_sequence_prompt)

    override fun onContentCreated(view: View) {
        shapeGrid = view.findViewById(R.id.sequence_shapes)
        progressRow = view.findViewById(R.id.sequence_progress)
        replayButton = view.findViewById(R.id.sequence_replay)

        buildShapes()
        buildProgressDots()
        replayButton.setOnClickListener { replay() }

        play()
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun buildShapes() {
        val count = game.shapeCount
        val columns = if (count <= 4) 2 else 3
        shapeGrid.columnCount = columns

        val tile = resources.getDimensionPixelSize(R.dimen.challenge_tile_size)
        val margin = resources.getDimensionPixelSize(R.dimen.challenge_tile_margin)

        for (index in 0 until count) {
            val shape = TextView(requireContext()).apply {
                text = ChallengeSymbols.glyph(index)
                setTextColor(ChallengeSymbols.color(requireContext(), index))
                gravity = Gravity.CENTER
                textSize = 30f
                alpha = UNLIT_ALPHA
                setBackgroundResource(R.drawable.challenge_tile_outlined)
                contentDescription =
                        getString(R.string.challenge_sequence_shape_description, index + 1)
                setOnClickListener { onShapeTapped(index) }
            }
            shapeViews += shape
            shapeGrid.addView(shape, GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns)).apply {
                width = tile
                height = tile
                setMargins(margin, margin, margin, margin)
            })
        }
    }

    private fun buildProgressDots() {
        progressRow.removeAllViews()
        dotViews.clear()
        val size = resources.getDimensionPixelSize(R.dimen.challenge_tile_margin)
        for (i in game.sequence.indices) {
            val dot = TextView(requireContext()).apply {
                text = "●"
                textSize = 10f
                setTextColor(ThemeUtils.resolveColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
                alpha = UNLIT_ALPHA
            }
            dotViews += dot
            progressRow.addView(dot, ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(size, 0, size, 0)
            })
        }
        renderProgress()
    }

    /** Plays the sequence back, lighting each shape in turn. */
    private fun play() {
        isPlaying = true
        replayButton.isEnabled = false
        showMessage(getString(R.string.challenge_sequence_watch))

        val tuning = ChallengeTuning.sequence((config as SequenceChallenge).difficulty)
        var at = tuning.gapMillis

        for (shape in game.sequence) {
            handler.postDelayed({
                if (!isAdded) return@postDelayed
                setLit(shape, true)
            }, at)
            at += tuning.litMillis
            handler.postDelayed({
                if (!isAdded) return@postDelayed
                setLit(shape, false)
            }, at)
            at += tuning.gapMillis
        }

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            isPlaying = false
            replayButton.isEnabled = true
            clearMessage()
        }, at)
    }

    /** Unlimited by design: this is a wake-up test, not a memory exam. */
    private fun replay() {
        if (isPlaying) return
        handler.removeCallbacksAndMessages(null)
        game.restartEntry()
        renderProgress()
        play()
    }

    private fun setLit(index: Int, lit: Boolean) {
        shapeViews[index].alpha = if (lit) 1f else UNLIT_ALPHA
        shapeViews[index].setBackgroundResource(
                if (lit) R.drawable.challenge_tile else R.drawable.challenge_tile_outlined)
    }

    private fun onShapeTapped(index: Int) {
        if (isPlaying) return

        // A brief flash so a tap feels answered.
        setLit(index, true)
        handler.postDelayed({
            if (isAdded) setLit(index, false)
        }, TAP_FLASH_MILLIS)

        when (game.tap(index)) {
            is SequenceGame.Tap.Wrong -> {
                showMessage(getString(R.string.challenge_sequence_wrong))
                renderProgress()
            }
            is SequenceGame.Tap.Complete -> {
                clearMessage()
                renderProgress()
                pass()
            }
            is SequenceGame.Tap.Correct -> {
                clearMessage()
                renderProgress()
            }
        }
    }

    private fun renderProgress() {
        for ((i, dot) in dotViews.withIndex()) {
            dot.alpha = if (i < game.matched) 1f else UNLIT_ALPHA
        }
    }

    companion object {
        private const val UNLIT_ALPHA = 0.35f
        private const val TAP_FLASH_MILLIS = 150L
    }
}
