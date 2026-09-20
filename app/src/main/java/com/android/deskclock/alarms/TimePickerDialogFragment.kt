/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.alarms

import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

import java.util.Calendar

/**
 * Shows the time picker used to create an alarm and to change an existing one.
 *
 * This is a helper rather than a fragment of its own because [MaterialTimePicker] is itself the
 * [androidx.fragment.app.DialogFragment], and it is final, so there is nothing left to wrap. The
 * cost of that is the result callback: a listener cannot survive the fragment being recreated,
 * so the host has to call [reattach] from its own onStart.
 */
object TimePickerDialogFragment {

    /**
     * Tag for the time picker fragment in the host's child FragmentManager.
     */
    private const val TAG = "TimePickerDialogFragment"

    /**
     * The callback interface used to indicate the user is done filling in the time (e.g. they
     * clicked on the 'OK' button).
     */
    interface OnTimeSetListener {
        /**
         * Called when the user is done setting a new time and the dialog has closed.
         *
         * @param hourOfDay the hour that was set
         * @param minute the minute that was set
         */
        fun onTimeSet(hourOfDay: Int, minute: Int)
    }

    @JvmStatic
    fun show(fragment: Fragment) {
        show(fragment, -1 /* hour */, -1 /* minute */)
    }

    fun show(parentFragment: Fragment, hourOfDay: Int, minute: Int) {
        require(parentFragment is OnTimeSetListener) {
            "Fragment must implement OnTimeSetListener"
        }

        val manager: FragmentManager = parentFragment.getChildFragmentManager()
        if (manager.isDestroyed()) {
            return
        }

        // Make sure the dialog isn't already added.
        removeTimeEditDialog(manager)

        val now = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(parentFragment.requireContext())) {
                    TimeFormat.CLOCK_24H
                } else {
                    TimeFormat.CLOCK_12H
                })
                .setHour(if (hourOfDay in 0..23) hourOfDay else now[Calendar.HOUR_OF_DAY])
                .setMinute(if (minute in 0..59) minute else now[Calendar.MINUTE])
                .build()
        bind(picker, parentFragment)
        picker.show(manager, TAG)
    }

    /**
     * Re-attaches the result listener to a picker that is already on screen. Call this from the
     * host's onStart: a click listener is not part of the fragment's saved state, so after a
     * rotation or a process death the picker would come back with its OK button wired to
     * nothing and silently drop the time the user chose.
     */
    @JvmStatic
    fun reattach(parentFragment: Fragment) {
        val picker = parentFragment.getChildFragmentManager()
                .findFragmentByTag(TAG) as? MaterialTimePicker
        picker?.let { bind(it, parentFragment) }
    }

    @JvmStatic
    fun removeTimeEditDialog(manager: FragmentManager?) {
        manager?.let {
            val prev = it.findFragmentByTag(TAG)
            prev?.let { fragment ->
                it.beginTransaction().remove(fragment).commit()
            }
        }
    }

    private fun bind(picker: MaterialTimePicker, parentFragment: Fragment) {
        // Clearing first keeps reattach idempotent: onStart can run more than once for one
        // picker, and every listener added would otherwise set the time again.
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener {
            (parentFragment as OnTimeSetListener).onTimeSet(picker.hour, picker.minute)
        }
    }
}
