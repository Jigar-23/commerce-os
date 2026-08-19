package com.commerceos.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE customerId = :customerId ORDER BY updatedAt DESC")
    fun observeCartItems(customerId: String): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE customerId = :customerId ORDER BY updatedAt DESC")
    suspend fun getCartItems(customerId: String): List<CartItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: CartItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CartItemEntity>)

    @Query("DELETE FROM cart_items WHERE sku = :sku AND customerId = :customerId")
    suspend fun deleteBySku(sku: String, customerId: String)

    @Query("DELETE FROM cart_items WHERE customerId = :customerId")
    suspend fun clearCart(customerId: String)
}
