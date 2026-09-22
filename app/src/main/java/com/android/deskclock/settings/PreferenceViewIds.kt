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

package com.android.deskclock.settings

import android.view.View
import androidx.preference.PreferenceGroup

/**
 * Says "no id" the way the framework spells it on every row of this preference tree.
 *
 * `Preference.onBindViewHolder` ends with `holder.itemView.setId(mViewId)`, and `mViewId` is zero
 * for any preference that never had [androidx.preference.Preference.setViewId] called on it, which
 * is every preference here. Zero is not a valid resource id, but it is not [View.NO_ID] either, so
 * it slips past the `id != NO_ID && !isViewIdGenerated(id)` guard in `View.onProvideStructure`:
 * `isViewIdGenerated` reads `(id and 0xFF000000) == 0 && (id and 0x00FFFFFF) != 0`, which zero
 * fails on the second half. Content capture then asks the resource table for the name of resource
 * zero once per row and the framework logs `Invalid resource ID 0x00000000.` at error level each
 * time. Nothing breaks, because the `NotFoundException` is caught and the row is recorded without
 * an id, and nothing shows at all on a device with no content capture service, which is why this
 * is invisible on a bare emulator. A settings screen that logs a dozen errors every time it is
 * built still buries anything real.
 *
 * [View.NO_ID] says what the zero was trying to say and the guard skips the lookup. Call this
 * before assigning any id of your own, so a real one still wins.
 */
internal fun PreferenceGroup.clearUnsetViewIds() {
    setViewId(View.NO_ID)
    for (i in 0 until preferenceCount) {
        val child = getPreference(i)
        if (child is PreferenceGroup) {
            child.clearUnsetViewIds()
        } else {
            child.setViewId(View.NO_ID)
        }
    }
}
