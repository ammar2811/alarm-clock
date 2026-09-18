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

package com.android.deskclock.challenges

import android.content.Context
import android.content.Intent
import com.android.deskclock.alarms.AlarmActivity
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.InstancesColumns

/**
 * The single decision point for whether a firing alarm may be dismissed yet.
 *
 * A firing alarm can be killed from several places: the ring screen's own gesture, the
 * volume and camera keys, the firing notification's Dismiss action, the public
 * com.android.deskclock.ALARM_DISMISS broadcast, and the ACTION_DISMISS_ALARM intent an
 * assistant sends. A gate that misses one of those is not a gate, so every one of them asks
 * [requiresChallenge] first and sends the user to [createChallengeIntent] instead of
 * dismissing.
 *
 * Note that AlarmStateManager.deleteInstanceAndUpdateParent is deliberately not gated. It is
 * also the cleanup path for missed and stale instances, and blocking it would leave rows
 * stranded in the database.
 */
object ChallengeGate {

    /**
     * Set on an [AlarmActivity] intent to open straight into the challenge flow, for the
     * dismiss paths that would otherwise have killed the alarm without showing anything.
     */
    const val EXTRA_START_CHALLENGE = "com.android.deskclock.extra.START_CHALLENGE"

    /**
     * True when [instance] is currently firing and still has challenges to complete.
     *
     * Anything else keeps its existing behaviour exactly, so an alarm without challenges
     * behaves like the app did before this feature.
     */
    @JvmStatic
    fun requiresChallenge(instance: AlarmInstance?): Boolean {
        return instance != null &&
                instance.mAlarmState == InstancesColumns.FIRED_STATE &&
                instance.mChallenges.isNotEmpty()
    }

    /** Brings the ring screen forward and starts this instance's challenges. */
    @JvmStatic
    fun createChallengeIntent(context: Context, instance: AlarmInstance): Intent {
        return AlarmInstance.createIntent(context, AlarmActivity::class.java, instance.mId)
                .putExtra(EXTRA_START_CHALLENGE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
