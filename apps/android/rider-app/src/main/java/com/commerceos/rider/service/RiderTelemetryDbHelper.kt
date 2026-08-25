package com.commerceos.rider.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.commerceos.rider.model.RiderLocationUpdate

/**
 * SQLite Database Helper for Durable High-Frequency Telemetry Storage.
 * Provides atomic transaction-backed enqueue and dequeue operations per delivery.
 * Replaces SharedPreferences JSON storage to prevent data loss and high GC overhead.
 */
class RiderTelemetryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "commerce_rider_telemetry.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "telemetry_queue"
        const val COLUMN_DELIVERY_ID = "delivery_id"
        const val COLUMN_SEQUENCE_NUMBER = "sequence_number"
        const val COLUMN_RIDER_ID = "rider_id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_SPEED_KMH = "speed_kmh"
        const val COLUMN_HEADING = "heading"
        const val COLUMN_ACCURACY_METERS = "accuracy_meters"
        const val COLUMN_EVENT_TYPE = "event_type"
        const val COLUMN_IS_CRITICAL = "is_critical"

        private const val MAX_QUEUE_SIZE = 10000
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DELIVERY_ID TEXT NOT NULL,
                $COLUMN_SEQUENCE_NUMBER INTEGER NOT NULL,
                $COLUMN_RIDER_ID TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_SPEED_KMH REAL NOT NULL,
                $COLUMN_HEADING REAL NOT NULL,
                $COLUMN_ACCURACY_METERS REAL NOT NULL,
                $COLUMN_EVENT_TYPE TEXT DEFAULT 'TELEMETRY_UPDATED',
                $COLUMN_IS_CRITICAL INTEGER DEFAULT 0,
                PRIMARY KEY ($COLUMN_DELIVERY_ID, $COLUMN_SEQUENCE_NUMBER)
            );
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Atomically enqueue a new telemetry update into SQLite.
     * Enforces explicit queue retention rules: if non-critical queue size exceeds max threshold,
     * trims oldest non-critical items without losing critical state transitions.
     */
    @Synchronized
    fun enqueue(update: RiderLocationUpdate, isCritical: Boolean = false): Boolean {
        if (update.deliveryId.isBlank()) return false

        val db = writableDatabase
        db.beginTransaction()
        try {
            val currentCount = getQueueSize()
            if (currentCount >= MAX_QUEUE_SIZE) {
                db.execSQL("""
                    DELETE FROM $TABLE_NAME 
                    WHERE rowid IN (
                        SELECT rowid FROM $TABLE_NAME 
                        WHERE $COLUMN_IS_CRITICAL = 0 
                        ORDER BY $COLUMN_TIMESTAMP ASC LIMIT ${currentCount - MAX_QUEUE_SIZE + 1}
                    )
                """.trimIndent())
            }

            val values = ContentValues().apply {
                put(COLUMN_DELIVERY_ID, update.deliveryId)
                put(COLUMN_SEQUENCE_NUMBER, update.sequenceNumber)
                put(COLUMN_RIDER_ID, update.riderId)
                put(COLUMN_TIMESTAMP, update.timestamp)
                put(COLUMN_LATITUDE, update.latitude)
                put(COLUMN_LONGITUDE, update.longitude)
                put(COLUMN_SPEED_KMH, update.speedKmh)
                put(COLUMN_HEADING, update.heading)
                put(COLUMN_ACCURACY_METERS, update.accuracyMeters)
                put(COLUMN_EVENT_TYPE, "TELEMETRY_UPDATED")
                put(COLUMN_IS_CRITICAL, if (isCritical) 1 else 0)
            }

            db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically fetch the head item for transmission restricted to active delivery session.
     * Prevents cross-delivery telemetry leakage (Requirement 41).
     */
    @Synchronized
    fun peekHead(activeDeliveryId: String? = null): RiderLocationUpdate? {
        val db = readableDatabase
        val selection = if (!activeDeliveryId.isNullOrBlank()) "$COLUMN_DELIVERY_ID = ?" else null
        val selectionArgs = if (!activeDeliveryId.isNullOrBlank()) arrayOf(activeDeliveryId) else null
        val cursor = db.query(
            TABLE_NAME,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC, $COLUMN_SEQUENCE_NUMBER ASC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                return RiderLocationUpdate(
                    deliveryId = it.getString(it.getColumnIndexOrThrow(COLUMN_DELIVERY_ID)),
                    sequenceNumber = it.getLong(it.getColumnIndexOrThrow(COLUMN_SEQUENCE_NUMBER)),
                    riderId = it.getString(it.getColumnIndexOrThrow(COLUMN_RIDER_ID)),
                    latitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE)),
                    longitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE)),
                    speedKmh = it.getFloat(it.getColumnIndexOrThrow(COLUMN_SPEED_KMH)),
                    heading = it.getFloat(it.getColumnIndexOrThrow(COLUMN_HEADING)),
                    accuracyMeters = it.getFloat(it.getColumnIndexOrThrow(COLUMN_ACCURACY_METERS)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                )
            }
        }
        return null
    }

    @Synchronized
    fun purgeInactiveDeliveries(activeDeliveryId: String) {
        if (activeDeliveryId.isBlank()) return
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID != ?", arrayOf(activeDeliveryId))
    }

    /**
     * Atomically delete telemetry up to the acknowledged sequence number for a delivery.
     * Safe against crash between server ACK and delete.
     */
    @Synchronized
    fun dequeueUpToSequence(deliveryId: String, ackSequenceNumber: Long): Int {
        if (deliveryId.isBlank()) return 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            val deletedRows = db.delete(
                TABLE_NAME,
                "$COLUMN_DELIVERY_ID = ? AND $COLUMN_SEQUENCE_NUMBER <= ?",
                arrayOf(deliveryId, ackSequenceNumber.toString())
            )
            db.setTransactionSuccessful()
            return deletedRows
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getQueueSize(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    @Synchronized
    fun getMaxSequenceNumber(deliveryId: String): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX($COLUMN_SEQUENCE_NUMBER) FROM $TABLE_NAME WHERE $COLUMN_DELIVERY_ID = ?",
            arrayOf(deliveryId)
        )
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                return it.getLong(0)
            }
        }
        return 0L
    }

    @Synchronized
    fun clearQueueForDelivery(deliveryId: String) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID = ?", arrayOf(deliveryId))
    }
}
