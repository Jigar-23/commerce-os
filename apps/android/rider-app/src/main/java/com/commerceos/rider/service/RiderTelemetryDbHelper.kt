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
 * Protects all sensitive rider and delivery metadata with Keystore AES/GCM encryption.
 */
class RiderTelemetryDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val secureStore by lazy { com.commerceos.rider.session.RiderSecureCredentialStore(context) }
    private var needsVacuum = false

    companion object {
        private const val TAG = "RiderTelemetryDbHelper"
        private const val DATABASE_NAME = "commerce_rider_telemetry.db"
        private const val DATABASE_VERSION = 3

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

    private fun hashDeliveryId(raw: String): String {
        if (raw.isBlank()) return ""
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (needsVacuum) {
            try {
                db.execSQL("VACUUM")
                needsVacuum = false
            } catch (e: Exception) {
                Log.w(TAG, "VACUUM purge failed: ${e.message}")
            }
        }
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
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        if (oldVersion < 3) {
            needsVacuum = true
            db.beginTransaction()
            try {
                val cursor = db.rawQuery("PRAGMA table_info($TABLE_NAME)", null)
                val existingCols = mutableSetOf<String>()
                cursor.use {
                    while (it.moveToNext()) {
                        existingCols.add(it.getString(1))
                    }
                }
                if (COLUMN_ENCRYPTED_COORDINATES !in existingCols) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ENCRYPTED_COORDINATES TEXT")
                }

                // Migrate all legacy rows to encrypt sensitive metadata
                val readCursor = db.rawQuery(
                    "SELECT rowid, $COLUMN_DELIVERY_ID, $COLUMN_SEQUENCE_NUMBER, $COLUMN_RIDER_ID, $COLUMN_TIMESTAMP, $COLUMN_LATITUDE, $COLUMN_LONGITUDE, $COLUMN_SPEED_KMH, $COLUMN_HEADING, $COLUMN_ACCURACY_METERS, $COLUMN_ENCRYPTED_COORDINATES FROM $TABLE_NAME",
                    null
                )
                data class MigrationRow(
                    val rowid: Long,
                    val deliveryId: String,
                    val seq: Long,
                    val riderId: String,
                    val timestamp: Long,
                    val lat: Double,
                    val lng: Double,
                    val speed: Float,
                    val heading: Float,
                    val accuracy: Float,
                    val encCoords: String?
                )
                val pending = mutableListOf<MigrationRow>()
                readCursor.use { c ->
                    while (c.moveToNext()) {
                        pending.add(
                            MigrationRow(
                                rowid = c.getLong(0),
                                deliveryId = c.getString(1) ?: "",
                                seq = c.getLong(2),
                                riderId = c.getString(3) ?: "",
                                timestamp = c.getLong(4),
                                lat = c.getDouble(5),
                                lng = c.getDouble(6),
                                speed = c.getFloat(7),
                                heading = c.getFloat(8),
                                accuracy = c.getFloat(9),
                                encCoords = c.getString(10)
                            )
                        )
                    }
                }

                for (row in pending) {
                    var finalLat = row.lat
                    var finalLng = row.lng
                    var finalSpeed = row.speed
                    var finalHeading: Float? = row.heading.takeIf { it >= 0f }
                    var finalAccuracy = row.accuracy
                    var finalRiderId = row.riderId
                    var finalDeliveryId = row.deliveryId
                    var finalTs = row.timestamp

                    if (!row.encCoords.isNullOrBlank()) {
                        val decrypted = secureStore.decrypt(row.encCoords)
                        if (decrypted != null) {
                            if (decrypted.startsWith("{")) {
                                val json = org.json.JSONObject(decrypted)
                                finalLat = json.optDouble("lat", finalLat)
                                finalLng = json.optDouble("lng", finalLng)
                                finalSpeed = json.optDouble("speed", finalSpeed.toDouble()).toFloat()
                                if (json.has("heading") && !json.isNull("heading")) {
                                    val h = json.getDouble("heading").toFloat()
                                    finalHeading = if (h >= 0f) h else null
                                }
                                finalAccuracy = json.optDouble("accuracy", finalAccuracy.toDouble()).toFloat()
                                finalRiderId = json.optString("riderId", finalRiderId)
                                finalDeliveryId = json.optString("deliveryId", finalDeliveryId)
                                finalTs = json.optLong("timestamp", finalTs)
                            } else if (decrypted.contains(",")) {
                                val parts = decrypted.split(",")
                                val pLat = parts.getOrNull(0)?.toDoubleOrNull()
                                val pLng = parts.getOrNull(1)?.toDoubleOrNull()
                                if (pLat != null && pLng != null) {
                                    finalLat = pLat
                                    finalLng = pLng
                                }
                            }
                        }
                    }

                    val fullPayload = org.json.JSONObject().apply {
                        put("lat", finalLat)
                        put("lng", finalLng)
                        put("speed", finalSpeed)
                        put("heading", finalHeading ?: -1.0f)
                        put("accuracy", finalAccuracy)
                        put("riderId", finalRiderId)
                        put("deliveryId", finalDeliveryId)
                        put("timestamp", finalTs)
                    }.toString()

                    val encrypted = secureStore.encrypt(fullPayload)
                        ?: throw IllegalStateException("Failed to encrypt telemetry record during upgrade")

                    val cv = ContentValues().apply {
                        put(COLUMN_ENCRYPTED_COORDINATES, encrypted)
                        put(COLUMN_DELIVERY_ID, hashDeliveryId(finalDeliveryId))
                        put(COLUMN_RIDER_ID, "REDACTED")
                        put(COLUMN_LATITUDE, 0.0)
                        put(COLUMN_LONGITUDE, 0.0)
                        put(COLUMN_SPEED_KMH, 0.0f)
                        put(COLUMN_HEADING, 0.0f)
                        put(COLUMN_ACCURACY_METERS, 0.0f)
                        put(COLUMN_TIMESTAMP, 0L)
                    }
                    db.update(TABLE_NAME, cv, "rowid = ?", arrayOf(row.rowid.toString()))
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
            db.delete(TABLE_NAME, "$COLUMN_TIMESTAMP > 0 AND $COLUMN_TIMESTAMP < ?", arrayOf(cutoff.toString()))
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Atomically enqueue a new telemetry update into SQLite.
     * Enforces explicit queue retention rules: if non-critical queue size exceeds max threshold,
     * trims oldest non-critical items without losing critical state transitions.
     * Encrypts all sensitive location & delivery metadata before persisting.
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
                        ORDER BY $COLUMN_SEQUENCE_NUMBER ASC LIMIT ${currentCount - MAX_QUEUE_SIZE + 1}
                    )
                """.trimIndent())
            }

            val payload = org.json.JSONObject().apply {
                put("lat", update.latitude)
                put("lng", update.longitude)
                put("speed", update.speedKmh)
                put("heading", update.heading ?: -1.0f)
                put("accuracy", update.accuracyMeters)
                put("riderId", update.riderId)
                put("deliveryId", update.deliveryId)
                put("timestamp", update.timestamp)
            }.toString()

            val encryptedPayload = secureStore.encrypt(payload)
            if (encryptedPayload == null) {
                Log.e(TAG, "Encryption failed for rider telemetry payload; failing closed to prevent plaintext exposure")
                return false
            }

            val hashedDeliveryId = hashDeliveryId(update.deliveryId)
            val values = ContentValues().apply {
                put(COLUMN_DELIVERY_ID, hashedDeliveryId)
                put(COLUMN_SEQUENCE_NUMBER, update.sequenceNumber)
                put(COLUMN_RIDER_ID, "REDACTED")
                put(COLUMN_TIMESTAMP, 0L)
                put(COLUMN_ENCRYPTED_COORDINATES, encryptedPayload)
                put(COLUMN_LATITUDE, 0.0)
                put(COLUMN_LONGITUDE, 0.0)
                put(COLUMN_SPEED_KMH, 0.0f)
                put(COLUMN_HEADING, 0.0f)
                put(COLUMN_ACCURACY_METERS, 0.0f)
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
        val targetDeliveryId: String = activeDeliveryId
        val db = readableDatabase
        val hashedDeliveryId = hashDeliveryId(targetDeliveryId)
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_DELIVERY_ID = ? OR $COLUMN_DELIVERY_ID = ?",
            arrayOf(hashedDeliveryId, targetDeliveryId),
            null,
            null,
            "$COLUMN_SEQUENCE_NUMBER ASC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                val encIdx = it.getColumnIndex(COLUMN_ENCRYPTED_COORDINATES)
                val encCoords = if (encIdx >= 0 && !it.isNull(encIdx)) it.getString(encIdx) else null
                var lat = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE))
                var lng = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                var speed = it.getFloat(it.getColumnIndexOrThrow(COLUMN_SPEED_KMH))
                var heading: Float? = it.getFloat(it.getColumnIndexOrThrow(COLUMN_HEADING)).takeIf { h -> h >= 0f }
                var acc = it.getFloat(it.getColumnIndexOrThrow(COLUMN_ACCURACY_METERS))
                var rId = it.getString(it.getColumnIndexOrThrow(COLUMN_RIDER_ID)) ?: ""
                var dId: String = targetDeliveryId
                var ts = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val seq = it.getLong(it.getColumnIndexOrThrow(COLUMN_SEQUENCE_NUMBER))

                if (!encCoords.isNullOrBlank()) {
                    val decrypted = secureStore.decrypt(encCoords)
                    if (decrypted != null) {
                        if (decrypted.startsWith("{")) {
                            val json = org.json.JSONObject(decrypted)
                            lat = json.optDouble("lat", lat)
                            lng = json.optDouble("lng", lng)
                            speed = json.optDouble("speed", speed.toDouble()).toFloat()
                            if (json.has("heading") && !json.isNull("heading")) {
                                val h = json.getDouble("heading").toFloat()
                                heading = if (h >= 0f) h else null
                            }
                            acc = json.optDouble("accuracy", acc.toDouble()).toFloat()
                            rId = json.optString("riderId", rId)
                            dId = json.optString("deliveryId", dId)
                            ts = json.optLong("timestamp", ts)
                        } else if (decrypted.contains(",")) {
                            val parts = decrypted.split(",")
                            val parsedLat = parts.getOrNull(0)?.toDoubleOrNull()
                            val parsedLng = parts.getOrNull(1)?.toDoubleOrNull()
                            if (parsedLat != null && parsedLng != null) {
                                lat = parsedLat
                                lng = parsedLng
                            }
                        }
                    }
                }
                return RiderLocationUpdate(
                    deliveryId = dId,
                    sequenceNumber = seq,
                    riderId = rId,
                    latitude = lat,
                    longitude = lng,
                    speedKmh = speed,
                    heading = heading,
                    accuracyMeters = acc,
                    timestamp = ts
                )
            }
        }
        return null
    }

    @Synchronized
    fun purgeInactiveDeliveries(activeDeliveryId: String) {
        if (activeDeliveryId.isBlank()) return
        val db = writableDatabase
        val hashed = hashDeliveryId(activeDeliveryId)
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID != ? AND $COLUMN_DELIVERY_ID != ?", arrayOf(hashed, activeDeliveryId))
    }

    @Synchronized
    fun purgeDelivery(deliveryId: String) {
        if (deliveryId.isBlank()) return
        val db = writableDatabase
        val hashed = hashDeliveryId(deliveryId)
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID = ? OR $COLUMN_DELIVERY_ID = ?", arrayOf(hashed, deliveryId))
    }

    /**
     * Atomically delete telemetry up to the acknowledged sequence number for a delivery.
     * Safe against crash between server ACK and delete.
     */
    @Synchronized
    fun dequeueUpToSequence(deliveryId: String, ackSequenceNumber: Long): Int {
        if (deliveryId.isBlank()) return 0
        val db = writableDatabase
        val hashed = hashDeliveryId(deliveryId)
        db.beginTransaction()
        try {
            val deletedRows = db.delete(
                TABLE_NAME,
                "($COLUMN_DELIVERY_ID = ? OR $COLUMN_DELIVERY_ID = ?) AND $COLUMN_SEQUENCE_NUMBER <= ?",
                arrayOf(hashed, deliveryId, ackSequenceNumber.toString())
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
        val hashed = hashDeliveryId(deliveryId)
        val cursor = db.rawQuery(
            "SELECT MAX($COLUMN_SEQUENCE_NUMBER) FROM $TABLE_NAME WHERE $COLUMN_DELIVERY_ID = ? OR $COLUMN_DELIVERY_ID = ?",
            arrayOf(hashed, deliveryId)
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
        val hashed = hashDeliveryId(deliveryId)
        db.delete(TABLE_NAME, "$COLUMN_DELIVERY_ID = ? OR $COLUMN_DELIVERY_ID = ?", arrayOf(hashed, deliveryId))
    }
}
