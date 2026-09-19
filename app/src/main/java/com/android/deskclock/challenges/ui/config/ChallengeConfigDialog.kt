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

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.android.deskclock.R
import com.android.deskclock.challenges.ChallengeCodec
import com.android.deskclock.challenges.ChallengeConfig
import com.android.deskclock.challenges.ChallengeSummary
import com.android.deskclock.challenges.Difficulty
import com.android.deskclock.challenges.MathChallenge
import com.android.deskclock.challenges.MemoryChallenge
import com.android.deskclock.challenges.PhotoChallenge
import com.android.deskclock.challenges.RetypeChallenge
import com.android.deskclock.challenges.SequenceChallenge
import com.android.deskclock.challenges.engine.MathProblemGenerator
import com.android.deskclock.challenges.engine.RetypeGenerator

/**
 * Settings for a single challenge: its difficulty, its own numbers, and a live preview of
 * what the result will actually ask for.
 *
 * The preview matters more than it looks. "Hard" means nothing on its own; an example
 * problem tells you immediately whether you have set something you can solve at 6am.
 */
class ChallengeConfigDialog : DialogFragment() {

    /** Implemented by the hosting screen to receive the edited config. */
    interface Listener {
        fun onChallengeConfigured(position: Int, config: ChallengeConfig)
        fun onChallengeConfigurationCancelled(position: Int, wasNew: Boolean)
    }

    private lateinit var working: ChallengeConfig
    private var position = 0
    private var saved = false

    private lateinit var previewView: TextView
    private lateinit var slidersView: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        position = args.getInt(ARG_POSITION)
        working = ChallengeCodec.decode(args.getString(ARG_CONFIG)).firstOrNull()
                ?: throw IllegalArgumentException("no challenge to configure")

        val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.challenge_config_dialog, null)
        previewView = view.findViewById(R.id.challenge_config_preview)
        slidersView = view.findViewById(R.id.challenge_config_sliders)

        buildDifficultyRow(view.findViewById(R.id.challenge_config_difficulty))
        buildSliders()
        updatePreview()

        view.findViewById<Button>(R.id.challenge_config_try).setOnClickListener {
            startActivity(ChallengePreviewActivity.createIntent(requireContext(), working))
        }

        return AlertDialog.Builder(requireContext())
                .setTitle(ChallengeSummary.nameOf(working.kind))
                .setView(view)
                .setPositiveButton(R.string.challenge_save) { _, _ ->
                    saved = true
                    (activity as? Listener)?.onChallengeConfigured(position, working)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!saved) {
            val wasNew = requireArguments().getBoolean(ARG_IS_NEW)
            (activity as? Listener)?.onChallengeConfigurationCancelled(position, wasNew)
        }
    }

    // ---------------------------------------------------------------- controls

    private fun buildDifficultyRow(container: ViewGroup) {
        for (difficulty in Difficulty.entries) {
            val button = LayoutInflater.from(requireContext())
                    .inflate(R.layout.challenge_difficulty_button, container, false) as TextView
            button.setText(ChallengeSummary.nameOf(difficulty))
            button.isSelected = working.difficulty == difficulty
            button.setOnClickListener {
                working = working.withDifficulty(difficulty)
                for (i in 0 until container.childCount) {
                    container.getChildAt(i).isSelected = false
                }
                button.isSelected = true
                updatePreview()
            }
            container.addView(button)
        }
    }

    private fun buildSliders() {
        slidersView.removeAllViews()
        when (val config = working) {
            is MathChallenge -> addSlider(R.string.challenge_setting_equations,
                    MathChallenge.EQUATIONS, config.equations) { value ->
                working = (working as MathChallenge).copy(equations = value)
            }

            is MemoryChallenge -> addSlider(R.string.challenge_setting_pairs,
                    MemoryChallenge.PAIRS, config.pairs) { value ->
                working = (working as MemoryChallenge).copy(pairs = value)
            }

            is RetypeChallenge -> {
                addSlider(R.string.challenge_setting_characters,
                        RetypeChallenge.LENGTH, config.length) { value ->
                    working = (working as RetypeChallenge).copy(length = value)
                }
                addSlider(R.string.challenge_setting_rounds,
                        RetypeChallenge.ROUNDS, config.rounds) { value ->
                    working = (working as RetypeChallenge).copy(rounds = value)
                }
            }

            is SequenceChallenge -> {
                addSlider(R.string.challenge_setting_shapes,
                        SequenceChallenge.SHAPES, config.shapes) { value ->
                    working = (working as SequenceChallenge).copy(shapes = value)
                }
                addSlider(R.string.challenge_setting_sequence_length,
                        SequenceChallenge.LENGTH, config.length) { value ->
                    working = (working as SequenceChallenge).copy(length = value)
                }
            }

            is PhotoChallenge -> {
                // Each photo has to be a different target, so the count cannot exceed the
                // number of targets. With only one target there is no choice to offer.
                val most = minOf(PhotoChallenge.PHOTOS.last, config.targets.size)
                if (most > PhotoChallenge.PHOTOS.first) {
                    addSlider(R.string.challenge_setting_photos,
                            PhotoChallenge.PHOTOS.first..most, config.photos) { value ->
                        working = (working as PhotoChallenge).copy(photos = value)
                    }
                }
                addTargetPicker()
            }
        }
    }

    private fun addSlider(
        labelRes: Int,
        range: IntRange,
        initial: Int,
        onChange: (Int) -> Unit,
    ) {
        val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.challenge_config_slider, slidersView, false)
        val label = row.findViewById<TextView>(R.id.slider_label)
        val value = row.findViewById<TextView>(R.id.slider_value)
        val bar = row.findViewById<SeekBar>(R.id.slider_bar)

        label.setText(labelRes)
        value.text = initial.toString()
        // SeekBar is zero based, so the range is carried as an offset.
        bar.max = range.last - range.first
        bar.progress = initial.coerceIn(range) - range.first

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val chosen = range.first + progress
                value.text = chosen.toString()
                onChange(chosen)
                updatePreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })

        slidersView.addView(row)
    }

    private fun addTargetPicker() {
        val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.challenge_config_slider, slidersView, false)
        row.findViewById<SeekBar>(R.id.slider_bar).visibility = View.GONE
        val label = row.findViewById<TextView>(R.id.slider_label)
        val value = row.findViewById<TextView>(R.id.slider_value)
        label.setText(R.string.challenge_setting_targets)

        fun refresh() {
            value.text = (working as PhotoChallenge).targets.size.toString()
        }
        refresh()

        row.setOnClickListener {
            PhotoTargetDialog.show(parentFragmentManager, (working as PhotoChallenge).targets)
            { chosen ->
                working = (working as PhotoChallenge).copy(targets = chosen).normalized()
                // Rebuilt rather than refreshed: how many targets there are decides the
                // photo count's range, and normalized() may just have clamped the count.
                buildSliders()
                updatePreview()
            }
        }
        slidersView.addView(row)
    }

    /** An example of what the configured challenge will actually ask for. */
    private fun updatePreview() {
        previewView.text = when (val config = working) {
            is MathChallenge ->
                getString(R.string.challenge_math_expression,
                        MathProblemGenerator.next(config.difficulty).expression)

            is RetypeChallenge ->
                RetypeGenerator.next(config.length, config.difficulty)

            is MemoryChallenge ->
                ChallengeSummary.describe(requireContext(), config)

            is SequenceChallenge ->
                ChallengeSummary.describe(requireContext(), config)

            is PhotoChallenge ->
                ChallengeSummary.describe(requireContext(), config)
        }
    }

    companion object {
        private const val ARG_POSITION = "position"
        private const val ARG_CONFIG = "config"
        private const val ARG_IS_NEW = "is_new"
        private const val TAG = "challenge_config"

        fun show(
            manager: FragmentManager,
            position: Int,
            config: ChallengeConfig,
            isNew: Boolean
        ) {
            // Replace any dialog already up, so a double tap cannot stack two.
            manager.findFragmentByTag(TAG)?.let {
                manager.beginTransaction().remove(it).commit()
            }
            ChallengeConfigDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                    putString(ARG_CONFIG, ChallengeCodec.encode(listOf(config)))
                    putBoolean(ARG_IS_NEW, isNew)
                }
            }.show(manager, TAG)
        }
    }
}

/** Returns a copy of this config at a different difficulty. */
private fun ChallengeConfig.withDifficulty(difficulty: Difficulty): ChallengeConfig = when (this) {
    is MathChallenge -> copy(difficulty = difficulty)
    is MemoryChallenge -> copy(difficulty = difficulty)
    is RetypeChallenge -> copy(difficulty = difficulty)
    is SequenceChallenge -> copy(difficulty = difficulty)
    is PhotoChallenge -> copy(difficulty = difficulty)
}
