/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Base activity class that lays content out behind the system bars.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let the content draw behind the status and navigation bars. This has to go through
        // WindowCompat rather than setSystemUiVisibility, because the light status bar flag
        // lives in the same bitmask: setting the layout flags directly wipes the value that
        // android:windowLightStatusBar put there, and the bar icons come out white on a
        // white surface in day mode.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
    }
}
