//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//


package com.github.quarck.calnotify.calendarmonitor

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.md5state

class CalendarMonitorStorageImplV2(val context: Context) {

    fun createDb(db: SQLiteDatabase) {
        val CREATE_PKG_TABLE =
                "CREATE " +
                        "TABLE $TABLE_NAME " +
                        "( " +
                        "$KEY_CALENDAR_ID INTEGER, " +

                        "$KEY_ALERT_TIME INTEGER, " +

                        "$KEY_INSTANCE_START INTEGER, " +

                        "$KEY_MD5A INTEGER, " +
                        "$KEY_MD5B INTEGER, " +
                        "$KEY_MD5C INTEGER, " +
                        "$KEY_MD5D INTEGER, " +

                        "$KEY_WE_CREATED_ALERT INTEGER, " +

                        "$KEY_WAS_HANDLED INTEGER, " +

                        "$KEY_RESERVED_INT1 INTEGER, " +
                        "$KEY_RESERVED_INT2 INTEGER, " +


                        "PRIMARY KEY ($KEY_MD5A, $KEY_MD5B, $KEY_MD5C, $KEY_MD5D, $KEY_ALERT_TIME, $KEY_INSTANCE_START)" +
                        " )"

        DevLog.debug(LOG_TAG, "Creating DB TABLE using query: " + CREATE_PKG_TABLE)

        db.execSQL(CREATE_PKG_TABLE)

        val CREATE_INDEX = "CREATE UNIQUE INDEX $INDEX_NAME ON $TABLE_NAME ($KEY_MD5A, $KEY_MD5B, $KEY_MD5C, $KEY_MD5D, $KEY_ALERT_TIME, $KEY_INSTANCE_START)"

        DevLog.debug(LOG_TAG, "Creating DB INDEX using query: " + CREATE_INDEX)

        db.execSQL(CREATE_INDEX)

    }

    fun addAlert(db: SQLiteDatabase, entry: MonitorEventAlertEntry) {

        //DevLog.debug(LOG_TAG, "addAlert $entry")

        val values = recordToContentValues(entry)

        try {
            db.insertOrThrow(TABLE_NAME, // table
                    null, // nullColumnHack
                    values) // key/value -> keys = column names/ values = column
            // values
        }
        catch (ex: SQLiteConstraintException) {
            DevLog.debug(LOG_TAG, "This entry ($entry) is already in the DB!, updating instead")
            updateAlert(db, entry)
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "addAlert($entry): exception ${ex.detailed}")
        }
    }

    fun addAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        try {
            db.beginTransaction()

            for (entry in entries)
                addAlert(db, entry)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    fun deleteAlert(db: SQLiteDatabase, a: Int, b: Int, c: Int, d: Int, alertTime: Long, instanceStart: Long) {

        //DevLog.debug(LOG_TAG, "deleteAlert $eventId / $alertTime")

        try {
            db.delete(
                    TABLE_NAME,
                    "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                    arrayOf(a.toString(), b.toString(),
                            c.toString(), d.toString(),
                            alertTime.toString(), instanceStart.toString()))
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "deleteAlert($a, $b, $c, $d, $alertTime): exception ${ex.detailed}")
        }
    }

    fun deleteAlert(db: SQLiteDatabase, e: MonitorEventAlertEntry) {
        try {
            db.delete(
                    TABLE_NAME,
                    "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                    arrayOf(e.md5.a.toString(), e.md5.b.toString(),
                            e.md5.c.toString(), e.md5.d.toString(),
                            e.alertTime.toString(), e.instanceStartTime.toString()))
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "deleteAlert($e): exception ${ex.detailed}")
        }
    }


    fun deleteAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        try {
            db.beginTransaction()

            for (entry in entries)
                deleteAlert(db, entry)

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    fun deleteAlertsMatching(db: SQLiteDatabase, filter: (MonitorEventAlertEntry) -> Boolean) {

        try {
            val alerts = getAlerts(db)

            db.beginTransaction()

            for (alert in alerts) {
                if (filter(alert)) {
                    deleteAlert(db, alert.md5.a, alert.md5.b, alert.md5.c, alert.md5.d, alert.alertTime, alert.instanceStartTime)
                }
            }

            db.setTransactionSuccessful()
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "deleteAlertsMatching: exception ${ex.detailed}")
        }
        finally {
            db.endTransaction()
        }
    }

    fun updateAlert(db: SQLiteDatabase, e: MonitorEventAlertEntry) {
        val values = recordToContentValues(e)

        db.update(TABLE_NAME, // table
                values, // column/value
                "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                arrayOf(e.md5.a.toString(), e.md5.b.toString(),
                        e.md5.c.toString(), e.md5.d.toString(),
                        e.alertTime.toString(), e.instanceStartTime.toString())
        )
    }

    fun updateAlerts(db: SQLiteDatabase, entries: Collection<MonitorEventAlertEntry>) {

        //DevLog.debug(LOG_TAG, "Updating ${entries.size} alerts");

        try {
            db.beginTransaction()

            for (e in entries) {
                //DevLog.debug(LOG_TAG, "Updating alert entry, eventId=${entry.eventId}, alertTime =${entry.alertTime}");

                val values = recordToContentValues(e)

                db.update(TABLE_NAME, // table
                        values, // column/value
                        "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                        arrayOf(e.md5.a.toString(), e.md5.b.toString(),
                                e.md5.c.toString(), e.md5.d.toString(),
                                e.alertTime.toString(), e.instanceStartTime.toString()))
            }

            db.setTransactionSuccessful()
        }
        finally {
            db.endTransaction()
        }
    }

    fun getAlert(db: SQLiteDatabase, md5: md5state, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry? {

        var ret: MonitorEventAlertEntry? = null

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_ALERT_TIME = ? AND $KEY_INSTANCE_START = ?",
                    arrayOf(md5.a.toString(), md5.b.toString(),
                            md5.c.toString(), md5.d.toString(),
                            alertTime.toString(), instanceStart.toString()),
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                ret = cursorToRecord(cursor)
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlert: exception $ex, stack: ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlert($eventId, $alertTime), returning ${ret}")

        return ret
    }


    fun getNextAlert(db: SQLiteDatabase, since: Long): Long? {

        var ret: Long? = null;

        val query = "SELECT MIN($KEY_ALERT_TIME) FROM $TABLE_NAME WHERE $KEY_ALERT_TIME >= $since"

        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery(query, null)

            if (cursor != null && cursor.moveToFirst())
                ret = cursor.getLong(0)

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getNextAlert: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getNextAlert, returning $ret")

        return ret
    }

    fun getAlertsAt(db: SQLiteDatabase, time: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    "$KEY_ALERT_TIME = ?",
                    arrayOf(time.toString()),
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlertsAt($time), returning ${ret.size} requests")

        return ret
    }

    fun getAlerts(db: SQLiteDatabase): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    null, // c. selections
                    null,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    fun getInstanceAlerts(db: SQLiteDatabase, md5: md5state, instanceStart: Long): List<MonitorEventAlertEntry> {

        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_MD5A = ? AND $KEY_MD5B = ? AND $KEY_MD5C = ? AND $KEY_MD5D = ? AND $KEY_INSTANCE_START = ?"
        val selectionArgs = arrayOf(md5.a.toString(), md5.b.toString(), md5.c.toString(), md5.d.toString(), instanceStart.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getInstanceAlerts: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    fun getAlertsForInstanceStartRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_INSTANCE_START >= ? AND $KEY_INSTANCE_START <= ?"
        val selectionArgs = arrayOf(scanFrom.toString(), scanTo.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        //DevLog.debug(LOG_TAG, "getAlerts, returnint ${ret.size} requests")

        return ret
    }

    fun getAlertsForAlertRange(db: SQLiteDatabase, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        var cursor: Cursor? = null

        val selection = "$KEY_ALERT_TIME >= ? AND $KEY_ALERT_TIME <= ?"
        val selectionArgs = arrayOf(scanFrom.toString(), scanTo.toString())

        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    selection,
                    selectionArgs,
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ret.add(cursorToRecord(cursor))
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getAlertsAt: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }
        return ret
    }

    fun dumpAll(db: SQLiteDatabase) {

        DevLog.debug(LOG_TAG, "Dumping monitor state: ")

        var cursor: Cursor? = null
        var i = 0
        try {
            cursor = db.query(TABLE_NAME, // a. table
                    SELECT_COLUMNS, // b. column names
                    null, // selection
                    null, // selection Args
                    null, // e. group by
                    null, // f. h aving
                    null, // g. order by
                    null) // h. limit

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val ent = cursorToRecord(cursor)
                    DevLog.debug(LOG_TAG, "${i++}: $ent")
                } while (cursor.moveToNext())
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "dumpAll: exception ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }
    }


    private fun recordToContentValues(entry: MonitorEventAlertEntry): ContentValues {
        val values = ContentValues();

        values.put(KEY_CALENDAR_ID, -1)
        values.put(KEY_ALERT_TIME, entry.alertTime)
        values.put(KEY_INSTANCE_START, entry.instanceStartTime);
        values.put(KEY_MD5A, entry.md5.a)
        values.put(KEY_MD5B, entry.md5.b)
        values.put(KEY_MD5C, entry.md5.c)
        values.put(KEY_MD5D, entry.md5.d)
        values.put(KEY_WE_CREATED_ALERT, if (entry.alertCreatedByUs) 1 else 0)
        values.put(KEY_WAS_HANDLED, if (entry.wasHandled) 1 else 0)

        // Fill reserved keys with some placeholders
        values.put(KEY_RESERVED_INT1, 0L)
        values.put(KEY_RESERVED_INT2, 0L)

        return values;
    }

    private fun cursorToRecord(cursor: Cursor): MonitorEventAlertEntry {

        val md5 = md5state (
                a = cursor.getInt(PROJECTION_KEY_MD5A),
                b = cursor.getInt(PROJECTION_KEY_MD5B),
                c = cursor.getInt(PROJECTION_KEY_MD5C),
                d = cursor.getInt(PROJECTION_KEY_MD5D),
            )

        return MonitorEventAlertEntry(
                alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
                instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
                md5 = md5,
                alertCreatedByUs = cursor.getInt(PROJECTION_KEY_WE_CREATED_ALERT) != 0,
                wasHandled = cursor.getInt(PROJECTION_KEY_WAS_HANDLED) != 0
        )
    }

    companion object {
        private const val LOG_TAG = "MonitorStorageImplV2"

        private const val TABLE_NAME = "manualAlertsV2"
        private const val INDEX_NAME = "manualAlertsV1IdxV2"

        private const val KEY_CALENDAR_ID = "cid"
        private const val KEY_ALERT_TIME = "alrt"
        private const val KEY_INSTANCE_START = "inst"
        private const val KEY_MD5A = "md5a"
        private const val KEY_MD5B = "md5b"
        private const val KEY_MD5C = "md5c"
        private const val KEY_MD5D = "md5d"
        private const val KEY_WE_CREATED_ALERT = "m"
        private const val KEY_WAS_HANDLED = "h"


        private const val KEY_RESERVED_INT1 = "i1"
        private const val KEY_RESERVED_INT2 = "i2"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_CALENDAR_ID,
                KEY_ALERT_TIME,
                KEY_INSTANCE_START,
                KEY_MD5A,
                KEY_MD5B,
                KEY_MD5C,
                KEY_MD5D,
                KEY_WE_CREATED_ALERT,
                KEY_WAS_HANDLED
        )

        const val PROJECTION_KEY_CALENDAR_ID = 0
        const val PROJECTION_KEY_ALERT_TIME = 1
        const val PROJECTION_KEY_INSTANCE_START = 2
        const val PROJECTION_KEY_MD5A = 3
        const val PROJECTION_KEY_MD5B = 4
        const val PROJECTION_KEY_MD5C = 5
        const val PROJECTION_KEY_MD5D = 6
        const val PROJECTION_KEY_WE_CREATED_ALERT = 7
        const val PROJECTION_KEY_WAS_HANDLED = 8
    }

}