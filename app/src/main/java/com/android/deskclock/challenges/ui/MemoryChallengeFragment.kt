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

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import com.android.deskclock.R
import com.android.deskclock.challenges.ChallengeTuning
import com.android.deskclock.challenges.MemoryChallenge
import com.android.deskclock.challenges.engine.MemoryBoard

/** Match every pair on a face-down board to dismiss the alarm. */
class MemoryChallengeFragment : ChallengeFragment() {

    private val handler = Handler(Looper.getMainLooper())

    private val board: MemoryBoard by lazy {
        val memory = config as MemoryChallenge
        runner.engine(engineKey) { MemoryBoard(memory.pairs) }
    }

    private lateinit var grid: GridLayout
    private val cardViews = mutableListOf<TextView>()

    override val contentLayoutRes: Int get() = R.layout.challenge_content_memory

    override val promptText: String get() = getString(R.string.challenge_memory_prompt)

    override fun onContentCreated(view: View) {
        grid = view.findViewById(R.id.memory_board)
        buildBoard()

        val tuning = ChallengeTuning.memory((config as MemoryChallenge).difficulty)
        if (tuning.previewMillis > 0 && board.cards.none { it.matched }) {
            // Opening look at the board on easier settings.
            showMessage(getString(R.string.challenge_memory_preview))
            board.revealAll()
            render()
            handler.postDelayed({
                if (!isAdded) return@postDelayed
                board.hideUnmatched()
                clearMessage()
                render()
            }, tuning.previewMillis)
        } else {
            render()
        }
    }

    override fun onDestroyView() {
        // Nothing scheduled here may run against a detached fragment.
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun buildBoard() {
        val size = board.size
        // Keep the grid close to square, capped at four columns so cards stay tappable.
        val columns = when {
            size <= 6 -> 3
            size <= 12 -> 4
            else -> 5
        }
        grid.columnCount = columns

        val tile = resources.getDimensionPixelSize(R.dimen.challenge_tile_size)
        val margin = resources.getDimensionPixelSize(R.dimen.challenge_tile_margin)

        for (position in 0 until size) {
            val card = TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                textSize = 30f
                setBackgroundResource(R.drawable.challenge_tile)
                contentDescription =
                        getString(R.string.challenge_memory_card_description, position + 1)
                setOnClickListener { onCardTapped(position) }
            }
            cardViews += card
            grid.addView(card, GridLayout.LayoutParams(
                    GridLayout.spec(position / columns),
                    GridLayout.spec(position % columns)).apply {
                width = tile
                height = tile
                setMargins(margin, margin, margin, margin)
            })
        }
    }

    private fun onCardTapped(position: Int) {
        when (val result = board.tap(position)) {
            is MemoryBoard.Tap.Ignored -> return

            is MemoryBoard.Tap.Mismatch -> {
                render()
                val peek = ChallengeTuning.memory((config as MemoryChallenge).difficulty)
                        .peekMillis
                handler.postDelayed({
                    if (!isAdded) return@postDelayed
                    board.flipBackMismatch()
                    render()
                }, peek)
            }

            is MemoryBoard.Tap.Solved -> {
                render()
                pass()
            }

            else -> {
                // Revealed or Match; both just redraw.
                check(result is MemoryBoard.Tap.Revealed || result is MemoryBoard.Tap.Match)
                render()
            }
        }
    }

    private fun render() {
        for ((position, card) in board.cards.withIndex()) {
            val view = cardViews[position]
            if (card.faceUp || card.matched) {
                view.text = ChallengeSymbols.glyph(card.symbol)
                view.setTextColor(ChallengeSymbols.color(requireContext(), card.symbol))
                // Matched pairs fade back so attention stays on what is left to find.
                view.alpha = if (card.matched) 0.4f else 1f
            } else {
                view.text = ""
                view.alpha = 1f
            }
        }
    }
}
