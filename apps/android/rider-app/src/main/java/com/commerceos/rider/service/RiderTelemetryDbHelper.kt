package com.commerceos.rider.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.commerceos.rider.model.RiderLocationUpdate

/**
 * SQLite Database Helper for Durable High-Frequency Telemetry Storage.
 * Provides atomic transaction-backed enqueue and dequeue operations per delivery.
 * Replaces SharedPreferences JSON storage to prevent data loss and high GC overhead.
 */
class RiderTelemetryDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val secureStore by lazy { com.commerceos.rider.session.RiderSecureCredentialStore(context) }

    companion object {
        private const val TAG = "RiderTelemetryDbHelper"
        private const val DATABASE_NAME = "commerce_rider_telemetry.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_NAME = "telemetry_queue"
        const val COLUMN_DELIVERY_ID = "delivery_id"
        const val COLUMN_SEQUENCE_NUMBER = "sequence_number"
        const val COLUMN_RIDER_ID = "rider_id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_ENCRYPTED_COORDINATES = "encrypted_coordinates"
        const val COLUMN_SPEED_KMH = "speed_kmh"
        const val COLUMN_HEADING = "heading"
        const val COLUMN_ACCURACY_METERS = "accuracy_meters"
        const val COLUMN_EVENT_TYPE = "event_type"
        const val COLUMN_IS_CRITICAL = "is_critical"

        private const val MAX_QUEUE_SIZE = 10000
        private const val RETENTION_PERIOD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DELIVERY_ID TEXT NOT NULL,
                $COLUMN_SEQUENCE_NUMBER INTEGER NOT NULL,
                $COLUMN_RIDER_ID TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_LATITUDE REAL DEFAULT 0.0,
                $COLUMN_LONGITUDE REAL DEFAULT 0.0,
                $COLUMN_ENCRYPTED_COORDINATES TEXT,
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
        // Versioned schema upgrades preserving queued rider telemetry rows
        if (oldVersion < 2) {
            db.beginTransaction()
            try {
                val cursor = db.rawQuery("PRAGMA table_info($TABLE_NAME)", null)
                val existingCols = mutableSetOf<String>()
                cursor.use {
                    while (it.moveToNext()) {
                        existingCols.add(it.getString(1))
                    }
                }
                if (COLUMN_EVENT_TYPE !in existingCols) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_EVENT_TYPE TEXT DEFAULT 'TELEMETRY_UPDATED'")
                }
                if (COLUMN_IS_CRITICAL !in existingCols) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_IS_CRITICAL INTEGER DEFAULT 0")
                }
                if (COLUMN_ENCRYPTED_COORDINATES !in existingCols) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ENCRYPTED_COORDINATES TEXT")
                }

                // Migrate existing plaintext coordinates to encrypted_coordinates, then zero plaintext columns
                val readCursor = db.rawQuery(
                    "SELECT rowid, $COLUMN_LATITUDE, $COLUMN_LONGITUDE FROM $TABLE_NAME WHERE ($COLUMN_LATITUDE != 0.0 OR $COLUMN_LONGITUDE != 0.0) AND $COLUMN_ENCRYPTED_COORDINATES IS NULL",
                    null
                )
                val pendingUpdates = mutableListOf<Triple<Long, Double, Double>>()
                readCursor.use { c ->
                    while (c.moveToNext()) {
                        pendingUpdates.add(Triple(c.getLong(0), c.getDouble(1), c.getDouble(2)))
                    }
                }
                for ((rowid, lat, lng) in pendingUpdates) {
                    val encrypted = secureStore.encrypt("$lat,$lng")
                    if (encrypted == null) {
                        throw IllegalStateException("Encryption failed during telemetry database migration; aborting migration to prevent data loss")
                    }
                    val cv = ContentValues().apply {
                        put(COLUMN_ENCRYPTED_COORDINATES, encrypted)
                        put(COLUMN_LATITUDE, 0.0)
                        put(COLUMN_LONGITUDE, 0.0)
                    }
                    db.update(TABLE_NAME, cv, "rowid = ?", arrayOf(rowid.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Synchronized
    fun pruneExpiredTelemetry(): Int {
        return try {
            val db = writableDatabase
            val cutoff = System.currentTimeMillis() - RETENTION_PERIOD_MS
            db.delete(TABLE_NAME, "$COLUMN_TIMESTAMP < ?", arrayOf(cutoff.toString()))
        } catch (_: Exception) {
            0
        }
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
            // Prune expired records older than 24 hours
            val cutoff = System.currentTimeMillis() - RETENTION_PERIOD_MS
            db.delete(TABLE_NAME, "$COLUMN_TIMESTAMP < ?", arrayOf(cutoff.toString()))

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

            val encryptedCoords = secureStore.encrypt("${update.latitude},${update.longitude}")
            if (encryptedCoords == null) {
                Log.e(TAG, "Encryption failed for rider telemetry coordinates; failing closed to prevent plaintext exposure")
                return false
            }

            val values = ContentValues().apply {
                put(COLUMN_DELIVERY_ID, update.deliveryId)
                put(COLUMN_SEQUENCE_NUMBER, update.sequenceNumber)
                put(COLUMN_RIDER_ID, update.riderId)
                put(COLUMN_TIMESTAMP, update.timestamp)
                put(COLUMN_ENCRYPTED_COORDINATES, encryptedCoords)
                put(COLUMN_LATITUDE, 0.0)
                put(COLUMN_LONGITUDE, 0.0)
                put(COLUMN_SPEED_KMH, update.speedKmh)
                put(COLUMN_HEADING, update.heading)
                put(COLUMN_ACCURACY_METERS, update.accuracyMeters)
                put(COLUMN_EVENT_TYPE, "TELEMETRY_UPDATED")
                put(COLUMN_IS_CRITICAL, if (isCritical) 1 else 0)
            }

            val rowId = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (rowId == -1L) {
                return false
            }
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically fetch the head item for transmission restricted strictly to active delivery session.
     * Never queries or leaks inactive or completed deliveries.
     */
    @Synchronized
    fun peekHead(activeDeliveryId: String? = null): RiderLocationUpdate? {
        if (activeDeliveryId.isNullOrBlank()) return null
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_DELIVERY_ID = ?",
            arrayOf(activeDeliveryId),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC, $COLUMN_SEQUENCE_NUMBER ASC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                val encIdx = it.getColumnIndex(COLUMN_ENCRYPTED_COORDINATES)
                val encCoords = if (encIdx >= 0 && !it.isNull(encIdx)) it.getString(encIdx) else null
                var lat = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE))
                var lng = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                if (!encCoords.isNullOrBlank()) {
                    val decrypted = secureStore.decrypt(encCoords)
                    if (decrypted != null && decrypted.contains(",")) {
                        val parts = decrypted.split(",")
                        val parsedLat = parts.getOrNull(0)?.toDoubleOrNull()
                        val parsedLng = parts.getOrNull(1)?.toDoubleOrNull()
                        if (parsedLat != null && parsedLng != null) {
                            lat = parsedLat
                            lng = parsedLng
                        }
                    }
                }
                return RiderLocationUpdate(
                    deliveryId = it.getString(it.getColumnIndexOrThrow(COLUMN_DELIVERY_ID)),
                    sequenceNumber = it.getLong(it.getColumnIndexOrThrow(COLUMN_SEQUENCE_NUMBER)),
                    riderId = it.getString(it.getColumnIndexOrThrow(COLUMN_RIDER_ID)),
                    latitude = lat,
                    longitude = lng,
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

    @Synchronized
    fun purgeDelivery(deliveryId: String) {
        if (deliveryId.isBlank()) return
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID = ?", arrayOf(deliveryId))
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
