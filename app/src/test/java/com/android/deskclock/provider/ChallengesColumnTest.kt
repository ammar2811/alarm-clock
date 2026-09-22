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

package com.android.deskclock.provider

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.android.deskclock.challenges.ChallengeCodec
import com.android.deskclock.challenges.Difficulty
import com.android.deskclock.challenges.MathChallenge
import com.android.deskclock.challenges.MemoryChallenge
import com.android.deskclock.challenges.PhotoChallenge
import com.android.deskclock.data.Weekdays
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the CHALLENGES column end to end: the database migration that adds it, the
 * provider projection that exposes it, and the model classes that read and write it.
 *
 * The migration is the one irreversible step in this feature, so it is pinned here against
 * a hand written copy of the version 8 schema rather than anything derived from current
 * code, which would happily agree with itself.
 */
@RunWith(RobolectricTestRunner::class)
class ChallengesColumnTest {

    private lateinit var context: Context
    private val resolver: ContentResolver get() = context.contentResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ClockDatabaseHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(ClockDatabaseHelper.DATABASE_NAME)
    }

    // ---------------------------------------------------------------- migration

    @Test
    fun upgradeFromVersion8_keepsExistingAlarmsAndAddsTheColumn() {
        Version8Database(context).writableDatabase.use { db ->
            db.execSQL("INSERT INTO ${ClockDatabaseHelper.ALARMS_TABLE_NAME} " +
                    "(_id, hour, minutes, daysofweek, enabled, vibrate, label, alert, " +
                    "delete_after_use) VALUES (7, 6, 45, 31, 1, 1, 'Existing', NULL, 0);")
            db.execSQL("INSERT INTO ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} " +
                    "(_id, year, month, day, hour, minutes, vibrate, label, alert, " +
                    "alarmstate, alarmid) VALUES (3, 2026, 8, 18, 6, 45, 1, 'Existing', " +
                    "NULL, 0, 7);")
        }

        ClockDatabaseHelper(context).readableDatabase.use { db ->
            assertEquals(9, db.version)

            // The pre-existing alarm survives, with its own values intact.
            db.rawQuery("SELECT hour, minutes, label, challenges FROM " +
                    "${ClockDatabaseHelper.ALARMS_TABLE_NAME} WHERE _id = 7", null).use { c ->
                assertTrue("migrated alarm is missing", c.moveToFirst())
                assertEquals(6, c.getInt(0))
                assertEquals(45, c.getInt(1))
                assertEquals("Existing", c.getString(2))
                // An alarm that predates the feature has no challenges.
                assertEquals(ChallengeCodec.EMPTY, c.getString(3))
            }

            // And so does its instance, which is what the ring screen reads.
            db.rawQuery("SELECT challenges FROM " +
                    "${ClockDatabaseHelper.INSTANCES_TABLE_NAME} WHERE _id = 3", null).use { c ->
                assertTrue("migrated instance is missing", c.moveToFirst())
                assertEquals(ChallengeCodec.EMPTY, c.getString(0))
            }
        }
    }

    @Test
    fun migratedDefault_decodesToNoChallenges() {
        Version8Database(context).writableDatabase.use { db ->
            db.execSQL("INSERT INTO ${ClockDatabaseHelper.ALARMS_TABLE_NAME} " +
                    "(_id, hour, minutes, daysofweek, enabled, vibrate, label, alert, " +
                    "delete_after_use) VALUES (1, 6, 45, 31, 1, 1, '', NULL, 0);")
        }

        ClockDatabaseHelper(context).readableDatabase.use { db ->
            db.rawQuery("SELECT challenges FROM " +
                    "${ClockDatabaseHelper.ALARMS_TABLE_NAME} WHERE _id = 1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(emptyList<Any>(), ChallengeCodec.decode(c.getString(0)))
            }
        }
    }

    @Test
    fun freshDatabase_hasTheColumnOnBothTables() {
        ClockDatabaseHelper(context).readableDatabase.use { db ->
            for (table in listOf(ClockDatabaseHelper.ALARMS_TABLE_NAME,
                    ClockDatabaseHelper.INSTANCES_TABLE_NAME)) {
                val columns = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                    while (c.moveToNext()) {
                        columns += c.getString(c.getColumnIndexOrThrow("name"))
                    }
                }
                assertTrue("$table is missing ${AlarmSettingColumns.CHALLENGES}, found $columns",
                        AlarmSettingColumns.CHALLENGES in columns)
            }
        }
    }

    @Test
    fun defaultAlarms_areCreatedWithNoChallenges() {
        // onCreate inserts its default alarms with an explicit column list, so the new
        // column has to come from its SQL default.
        ClockDatabaseHelper(context).readableDatabase.use { db ->
            db.rawQuery("SELECT challenges FROM " +
                    "${ClockDatabaseHelper.ALARMS_TABLE_NAME}", null).use { c ->
                assertTrue("no default alarms were created", c.count > 0)
                while (c.moveToNext()) {
                    assertEquals(ChallengeCodec.EMPTY, c.getString(0))
                }
            }
        }
    }

    // ---------------------------------------------------------------- round trips

    @Test
    fun alarm_roundTripsItsChallengesThroughTheProvider() {
        val alarm = Alarm(6, 30).apply {
            daysOfWeek = Weekdays.NONE
            challenges = listOf(
                MathChallenge(id = "m", equations = 4, difficulty = Difficulty.HARD),
                PhotoChallenge(id = "p", targets = listOf("sink", "cup"), photos = 2),
            )
        }

        val saved = Alarm.addAlarm(resolver, alarm)
        val loaded = Alarm.getAlarm(resolver, saved.id)

        assertEquals(alarm.challenges, loaded!!.challenges)
    }

    @Test
    fun alarmInstance_roundTripsItsChallengesThroughTheProvider() {
        val instance = AlarmInstance(Calendar.getInstance()).apply {
            mChallenges = listOf(MemoryChallenge(id = "mem", pairs = 6))
        }

        val saved = AlarmInstance.addInstance(resolver, instance)
        val loaded = AlarmInstance.getInstance(resolver, saved.mId)

        assertEquals(instance.mChallenges, loaded!!.mChallenges)
    }

    @Test
    fun createInstanceAfter_snapshotsTheAlarmsChallenges() {
        // The ring screen reads the instance, so the instance must carry its own copy.
        // Editing the alarm later must not change an instance already scheduled.
        val alarm = Alarm(7, 0).apply {
            challenges = listOf(MathChallenge(id = "m", equations = 2))
        }

        val instance = alarm.createInstanceAfter(Calendar.getInstance())

        assertEquals(alarm.challenges, instance.mChallenges)
    }

    @Test
    fun joinedQuery_exposesChallengesFromBothTables() {
        // The provider applies a projection map to this join, and that map is a whitelist:
        // a column missing from it makes SQLiteQueryBuilder reject the query outright.
        val projection = arrayOf(
                "${ClockDatabaseHelper.ALARMS_TABLE_NAME}.${AlarmSettingColumns.CHALLENGES}",
                "${ClockDatabaseHelper.INSTANCES_TABLE_NAME}.${AlarmSettingColumns.CHALLENGES}",
        )
        val alarm = Alarm(8, 15).apply {
            enabled = true
            challenges = listOf(MathChallenge(id = "m", equations = 3))
        }
        val saved = Alarm.addAlarm(resolver, alarm)
        AlarmInstance.addInstance(resolver, saved.createInstanceAfter(Calendar.getInstance()))

        val selection = "${ClockDatabaseHelper.ALARMS_TABLE_NAME}._id = ?"
        val selectionArgs = arrayOf(saved.id.toString())
        resolver.query(AlarmsColumns.ALARMS_WITH_INSTANCES_URI, projection, selection,
                selectionArgs, null)
                .use { c ->
                    assertTrue("joined query returned no rows", c!!.moveToFirst())
                    assertEquals(saved.challenges, ChallengeCodec.decode(c.getString(0)))
                    assertEquals(saved.challenges, ChallengeCodec.decode(c.getString(1)))
                }
    }

    /** The alarms and instances schema exactly as version 8 shipped it, without the column. */
    private class Version8Database(context: Context)
        : SQLiteOpenHelper(context, ClockDatabaseHelper.DATABASE_NAME, null, 8) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE ${ClockDatabaseHelper.ALARMS_TABLE_NAME} (" +
                    "_id INTEGER PRIMARY KEY," +
                    "hour INTEGER NOT NULL, " +
                    "minutes INTEGER NOT NULL, " +
                    "daysofweek INTEGER NOT NULL, " +
                    "enabled INTEGER NOT NULL, " +
                    "vibrate INTEGER NOT NULL, " +
                    "label TEXT NOT NULL, " +
                    "alert TEXT, " +
                    "delete_after_use INTEGER NOT NULL DEFAULT 0);")
            db.execSQL("CREATE TABLE ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} (" +
                    "_id INTEGER PRIMARY KEY," +
                    "year INTEGER NOT NULL, " +
                    "month INTEGER NOT NULL, " +
                    "day INTEGER NOT NULL, " +
                    "hour INTEGER NOT NULL, " +
                    "minutes INTEGER NOT NULL, " +
                    "vibrate INTEGER NOT NULL, " +
                    "label TEXT NOT NULL, " +
                    "alert TEXT, " +
                    "alarmstate INTEGER NOT NULL, " +
                    "alarmid INTEGER REFERENCES " +
                    "${ClockDatabaseHelper.ALARMS_TABLE_NAME}(_id) " +
                    "ON UPDATE CASCADE ON DELETE CASCADE);")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
