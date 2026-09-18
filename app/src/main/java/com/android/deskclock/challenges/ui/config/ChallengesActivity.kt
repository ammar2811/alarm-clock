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

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.deskclock.BaseActivity
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.actionbarmenu.NavUpMenuItemController
import com.android.deskclock.actionbarmenu.OptionsMenuManager
import com.android.deskclock.alarms.AlarmUpdateHandler
import com.android.deskclock.challenges.ChallengeConfig
import com.android.deskclock.challenges.ChallengeKind
import com.android.deskclock.challenges.ChallengeSummary
import com.android.deskclock.challenges.PhotoChallenge
import com.android.deskclock.challenges.defaultChallengeOf
import com.android.deskclock.provider.Alarm

/**
 * Chooses which challenges an alarm requires before it can be dismissed, and how hard each
 * one is.
 *
 * Edits are kept in memory and written back when the screen closes, following the same
 * pattern as the ringtone picker: load the alarm by id, persist through AlarmUpdateHandler
 * as a minor update so already-scheduled instances pick the change up without being
 * rebuilt.
 */
class ChallengesActivity : BaseActivity(), ChallengeConfigDialog.Listener {

    private val optionsMenuManager = OptionsMenuManager()

    private var alarmId: Long = Alarm.INVALID_ID
    private val challenges = mutableListOf<ChallengeConfig>()
    private var loaded = false

    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var permissionBanner: View
    private lateinit var adapter: ChallengeListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_challenges)
        setTitle(R.string.challenges_title)
        // Same up affordance the settings screen uses, so back behaves consistently.
        optionsMenuManager.addMenuItemController(NavUpMenuItemController(this))

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, Alarm.INVALID_ID)

        emptyView = findViewById(R.id.challenges_empty)
        permissionBanner = findViewById(R.id.challenges_permission_banner)
        listView = findViewById(R.id.challenges_list)

        adapter = ChallengeListAdapter(
                challenges = challenges,
                onEdit = { position -> editChallenge(position) },
                onRemove = { position -> removeChallenge(position) },
                onReordered = { render() },
        )
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter
        ItemTouchHelper(adapter.dragCallback).attachToRecyclerView(listView)

        findViewById<View>(R.id.challenges_add).setOnClickListener { showTypePicker() }
        findViewById<View>(R.id.challenges_grant_camera).setOnClickListener {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }

        loadAlarm(savedInstanceState)
    }

    private fun loadAlarm(savedInstanceState: Bundle?) {
        val restored = savedInstanceState?.getString(STATE_CHALLENGES)
        if (restored != null) {
            challenges += com.android.deskclock.challenges.ChallengeCodec.decode(restored)
            loaded = true
            render()
            return
        }

        // The provider read is cheap, but it is still a disk read, so keep it off the main
        // thread and populate when it lands.
        Thread {
            val alarm = Alarm.getAlarm(contentResolver, alarmId)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (alarm == null) {
                    LOGGER.e("No alarm for id %d", alarmId)
                    finish()
                    return@runOnUiThread
                }
                challenges += alarm.challenges
                loaded = true
                render()
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CHALLENGES,
                com.android.deskclock.challenges.ChallengeCodec.encode(challenges))
    }

    override fun onStop() {
        // Persisted on the way out rather than per edit, so a burst of slider changes is one
        // write. Mirrors how the ringtone picker saves.
        if (loaded && !isChangingConfigurations) {
            save()
        }
        super.onStop()
    }

    private fun save() {
        val snapshot = challenges.toList()
        val id = alarmId
        Thread {
            val alarm = Alarm.getAlarm(contentResolver, id) ?: return@Thread
            if (alarm.challenges == snapshot) return@Thread
            alarm.challenges = snapshot
            AlarmUpdateHandler(applicationContext, null, null)
                    .asyncUpdateAlarm(alarm, false /* popToast */, true /* minorUpdate */)
        }.start()
    }

    // ---------------------------------------------------------------- editing

    private fun showTypePicker() {
        val kinds = ChallengeKind.entries
        val labels = kinds.map { getString(ChallengeSummary.nameOf(it)) }.toTypedArray()

        AlertDialog.Builder(this)
                .setTitle(R.string.challenge_add)
                .setItems(labels) { _, which ->
                    val kind = kinds[which]
                    challenges += defaultChallengeOf(kind)
                    render()
                    // Open the new one straight away, so adding and configuring is one move.
                    editChallenge(challenges.lastIndex)
                }
                .show()
    }

    private fun editChallenge(position: Int) {
        val config = challenges.getOrNull(position) ?: return
        ChallengeConfigDialog.show(supportFragmentManager, position, config)
    }

    private fun removeChallenge(position: Int) {
        if (position !in challenges.indices) return
        challenges.removeAt(position)
        adapter.notifyItemRemoved(position)
        render()
    }

    override fun onChallengeConfigured(position: Int, config: ChallengeConfig) {
        if (position !in challenges.indices) return
        challenges[position] = config
        adapter.notifyItemChanged(position)
        render()
    }

    override fun onChallengeConfigurationCancelled(position: Int, wasNew: Boolean) {
        // Backing out of the sheet for a challenge that was only just added should not leave
        // it behind half configured.
        if (wasNew) removeChallenge(position)
    }

    private fun render() {
        val empty = challenges.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        permissionBanner.visibility =
                if (needsCameraPermission()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    /** True when a photo challenge is configured but the camera is not available to it. */
    private fun needsCameraPermission(): Boolean {
        if (challenges.none { it is PhotoChallenge }) return false
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        optionsMenuManager.onCreateOptionsMenu(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        optionsMenuManager.onPrepareOptionsMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        optionsMenuManager.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) render()
    }

    companion object {
        private const val EXTRA_ALARM_ID = "com.android.deskclock.extra.ALARM_ID"
        private const val STATE_CHALLENGES = "challenges"
        private const val REQUEST_CAMERA = 1
        private val LOGGER = LogUtils.Logger("ChallengesActivity")

        @JvmStatic
        fun createIntent(context: Context, alarm: Alarm): Intent =
            Intent(context, ChallengesActivity::class.java)
                    .putExtra(EXTRA_ALARM_ID, alarm.id)
    }
}
