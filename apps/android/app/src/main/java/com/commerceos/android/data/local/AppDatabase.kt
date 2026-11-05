package com.commerceos.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CartItemEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cart_items_new (
                        customerId TEXT NOT NULL,
                        sku TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        unitPrice REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        verticalId TEXT NOT NULL,
                        merchantId TEXT,
                        mrp REAL NOT NULL,
                        brand TEXT,
                        packSize TEXT,
                        image TEXT,
                        prescriptionRequired INTEGER NOT NULL,
                        coldChain INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(customerId, sku)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO cart_items_new (
                        customerId, sku, productId, name, unitPrice, quantity, verticalId, merchantId, mrp, brand, packSize, image, prescriptionRequired, coldChain, updatedAt
                    )
                    SELECT customerId, sku, productId, name, unitPrice, quantity, verticalId, merchantId, mrp, brand, packSize, image, prescriptionRequired, coldChain, updatedAt
                    FROM cart_items
                """.trimIndent())
                db.execSQL("DROP TABLE cart_items")
                db.execSQL("ALTER TABLE cart_items_new RENAME TO cart_items")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "commerce_os_cart.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }
    }
}
