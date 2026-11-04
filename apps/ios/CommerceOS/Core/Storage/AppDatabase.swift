import Foundation

/**
 * 1:1 Parity with Android AppDatabase.kt (28 lines).
 * Authoritative local database container managing persistent table DAOs.
 */
public final class AppDatabase {
    public static let shared = AppDatabase()

    public let cartDao: CartDao

    private init() {
        self.cartDao = CartDao()
    }
}
