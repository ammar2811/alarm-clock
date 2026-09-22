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

package com.android.deskclock.challenges

import android.content.ContentResolver
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
 * Editing the alarm is a way to kill it too, because a major edit deletes and re-creates its
 * instances. Leaving the ring screen does not stop the alarm, so from the alarm list the user
 * could otherwise switch it off, delete it, move its time or change its repeat days, and an
 * assistant's ACTION_SET_ALARM for the same time would replace it. Those paths ask
 * [firingInstanceOf] and refuse while it returns an instance. A firing instance also keeps the
 * challenges it fired with, so editing the alarm's challenges only affects later occurrences.
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

    /**
     * The instance of the alarm with [alarmId] that is firing with challenges still to
     * complete, or null when that alarm may be edited or deleted freely.
     */
    @JvmStatic
    fun firingInstanceOf(cr: ContentResolver, alarmId: Long): AlarmInstance? {
        return AlarmInstance.getInstancesByAlarmId(cr, alarmId).firstOrNull(::requiresChallenge)
    }

    /** Brings the ring screen forward and starts this instance's challenges. */
    @JvmStatic
    fun createChallengeIntent(context: Context, instance: AlarmInstance): Intent {
        return AlarmInstance.createIntent(context, AlarmActivity::class.java, instance.mId)
                .putExtra(EXTRA_START_CHALLENGE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
