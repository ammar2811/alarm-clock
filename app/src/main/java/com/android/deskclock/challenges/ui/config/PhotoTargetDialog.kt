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

package com.android.deskclock.challenges.ui.config

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.android.deskclock.R
import com.android.deskclock.challenges.CocoLabels

import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Picks which objects satisfy a photo challenge.
 *
 * The suggested ones come first because they are the point of the exercise: things that are
 * normally in another room, so the alarm cannot be beaten from under the duvet. The full
 * list follows for anything else the bundled model can recognise.
 */
class PhotoTargetDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val selected = requireArguments().getStringArrayList(ARG_SELECTED).orEmpty().toMutableSet()

        // Suggested first, then everything else, with no duplicates between the two.
        val ordered = CocoLabels.SUGGESTED + (CocoLabels.ALL - CocoLabels.SUGGESTED.toSet())
        val labels = ordered.map { it.replaceFirstChar(Char::uppercase) }.toTypedArray()
        val checked = ordered.map { it in selected }.toBooleanArray()

        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.challenge_setting_targets)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    if (isChecked) selected += ordered[which] else selected -= ordered[which]
                }
                .setPositiveButton(R.string.challenge_save) { _, _ ->
                    // Keep the model's own ordering so the saved list is stable.
                    listener?.invoke(ordered.filter { it in selected })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
    }

    override fun onDestroy() {
        listener = null
        super.onDestroy()
    }

    companion object {
        private const val ARG_SELECTED = "selected"
        private const val TAG = "photo_targets"

        /**
         * Held statically because the result is a list of strings going straight back to a
         * dialog that is still on screen; a configuration change dismisses both together.
         */
        private var listener: ((List<String>) -> Unit)? = null

        fun show(
            manager: FragmentManager,
            selected: List<String>,
            onChosen: (List<String>) -> Unit,
        ) {
            listener = onChosen
            PhotoTargetDialog().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_SELECTED, ArrayList(selected))
                }
            }.show(manager, TAG)
        }
    }
}
