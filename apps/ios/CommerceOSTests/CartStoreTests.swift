import XCTest
import Foundation

final class CartStoreTests: XCTestCase {
    private var cartStore: CartLocalStore!
    
    override func setUp() {
        super.setUp()
        cartStore = CartLocalStore()
        cartStore.clear()
    }
    
    override func tearDown() {
        cartStore.clear()
        cartStore = nil
        super.tearDown()
    }
    
    private func createSampleProduct(sku: String, price: Double, rxRequired: Bool = false) -> ProductDto {
        return ProductDto(
            id: "PROD-\(sku)",
            sku: sku,
            name: "Sample Product \(sku)",
            brand: "HealthCorp",
            mrp: price * 1.2,
            price: price,
            category: "Pharma",
            saltComposition: "Paracetamol 500mg",
            requiresPrescription: rxRequired,
            isColdChain: false,
            inStock: true,
            genericSubstitute: nil
        )
    }
    
    func testCartStartsEmpty() {
        XCTAssertEqual(cartStore.totalItemCount, 0)
        XCTAssertEqual(cartStore.subtotal, 0.0)
        XCTAssertFalse(cartStore.hasPrescriptionItem)
        XCTAssertTrue(cartStore.items.isEmpty)
    }
    
    func testAddItem() {
        let prod = createSampleProduct(sku: "MED-001", price: 49.50)
        cartStore.add(product: prod)
        
        XCTAssertEqual(cartStore.totalItemCount, 1)
        XCTAssertEqual(cartStore.subtotal, 49.50)
        XCTAssertEqual(cartStore.items["MED-001"]?.quantity, 1)
    }
    
    func testAddDuplicateIncrementsQuantity() {
        let prod = createSampleProduct(sku: "MED-001", price: 100.0)
        cartStore.add(product: prod)
        cartStore.add(product: prod)
        
        XCTAssertEqual(cartStore.totalItemCount, 2)
        XCTAssertEqual(cartStore.subtotal, 200.0)
        XCTAssertEqual(cartStore.items["MED-001"]?.quantity, 2)
    }
    
    func testIncrementAndDecrement() {
        let prod = createSampleProduct(sku: "MED-002", price: 150.0)
        cartStore.add(product: prod)
        
        cartStore.increment(sku: "MED-002")
        XCTAssertEqual(cartStore.items["MED-002"]?.quantity, 2)
        XCTAssertEqual(cartStore.subtotal, 300.0)
        
        cartStore.decrement(sku: "MED-002")
        XCTAssertEqual(cartStore.items["MED-002"]?.quantity, 1)
        XCTAssertEqual(cartStore.subtotal, 150.0)
        
        // Decrementing when quantity == 1 must remove item
        cartStore.decrement(sku: "MED-002")
        XCTAssertNil(cartStore.items["MED-002"])
        XCTAssertEqual(cartStore.totalItemCount, 0)
        XCTAssertEqual(cartStore.subtotal, 0.0)
    }
    
    func testPrescriptionItemDetection() {
        let otcProd = createSampleProduct(sku: "OTC-01", price: 20.0, rxRequired: false)
        let rxProd = createSampleProduct(sku: "RX-01", price: 80.0, rxRequired: true)
        
        cartStore.add(product: otcProd)
        XCTAssertFalse(cartStore.hasPrescriptionItem)
        
        cartStore.add(product: rxProd)
        XCTAssertTrue(cartStore.hasPrescriptionItem)
        
        cartStore.decrement(sku: "RX-01")
        XCTAssertFalse(cartStore.hasPrescriptionItem)
    }
    
    func testSubtotalWithMultipleItems() {
        let item1 = createSampleProduct(sku: "ITEM-A", price: 12.50)
        let item2 = createSampleProduct(sku: "ITEM-B", price: 30.00)
        
        cartStore.add(product: item1)
        cartStore.add(product: item1) // 2 * 12.50 = 25.0
        cartStore.add(product: item2) // 1 * 30.00 = 30.0
        
        XCTAssertEqual(cartStore.totalItemCount, 3)
        XCTAssertEqual(cartStore.subtotal, 55.00, accuracy: 0.001)
    }
    
    func testClearCart() {
        let prod = createSampleProduct(sku: "MED-999", price: 250.0)
        cartStore.add(product: prod)
        cartStore.add(product: prod)
        XCTAssertFalse(cartStore.items.isEmpty)
        
        cartStore.clear()
        XCTAssertTrue(cartStore.items.isEmpty)
        XCTAssertEqual(cartStore.totalItemCount, 0)
        XCTAssertEqual(cartStore.subtotal, 0.0)
    }

    func testCartPersistenceAcrossStoreInstances() {
        let suiteName = "test_cart_suite_\(UUID().uuidString)"
        let testDefaults = UserDefaults(suiteName: suiteName)!
        
        let storeA = CartLocalStore(userDefaults: testDefaults, loadPersisted: false)
        storeA.clear()
        
        let prod1 = createSampleProduct(sku: "MED-PERSIST-1", price: 120.0)
        let prod2 = createSampleProduct(sku: "MED-PERSIST-2", price: 45.0)
        storeA.add(product: prod1)
        storeA.add(product: prod1)
        storeA.add(product: prod2)
        
        XCTAssertEqual(storeA.totalItemCount, 3)
        XCTAssertEqual(storeA.subtotal, 285.0)
        
        // Emulate fresh app launch by creating storeB using same persistent UserDefaults
        let storeB = CartLocalStore(userDefaults: testDefaults, loadPersisted: true)
        XCTAssertEqual(storeB.totalItemCount, 3)
        XCTAssertEqual(storeB.subtotal, 285.0)
        XCTAssertEqual(storeB.items["MED-PERSIST-1"]?.quantity, 2)
        XCTAssertEqual(storeB.items["MED-PERSIST-2"]?.quantity, 1)
        
        storeB.clear()
        testDefaults.removePersistentDomain(forName: suiteName)
    }

    func testReconcileWithCatalog() {
        let prodInStock = createSampleProduct(sku: "MED-IN-STOCK", price: 100.0)
        let prodOutStock = createSampleProduct(sku: "MED-OUT-OF-STOCK", price: 50.0)
        
        cartStore.add(product: prodInStock)
        cartStore.add(product: prodOutStock)
        XCTAssertEqual(cartStore.totalItemCount, 2)
        
        // Catalog returns prodInStock with new price 115.0 and prodOutStock marked inStock: false
        let liveInStock = ProductDto(
            id: prodInStock.id,
            sku: prodInStock.sku,
            name: prodInStock.name,
            brand: prodInStock.brand,
            mrp: prodInStock.mrp,
            price: 115.0,
            category: prodInStock.category,
            saltComposition: prodInStock.saltComposition,
            requiresPrescription: prodInStock.requiresPrescription,
            isColdChain: prodInStock.isColdChain,
            inStock: true,
            genericSubstitute: nil
        )
        let liveOutOfStock = ProductDto(
            id: prodOutStock.id,
            sku: prodOutStock.sku,
            name: prodOutStock.name,
            brand: prodOutStock.brand,
            mrp: prodOutStock.mrp,
            price: prodOutStock.price,
            category: prodOutStock.category,
            saltComposition: prodOutStock.saltComposition,
            requiresPrescription: prodOutStock.requiresPrescription,
            isColdChain: prodOutStock.isColdChain,
            inStock: false,
            genericSubstitute: nil
        )
        
        let result = cartStore.reconcileWithCatalog(liveProducts: [liveInStock, liveOutOfStock])
        XCTAssertEqual(result.removedCount, 1, "Should remove out of stock item")
        XCTAssertEqual(result.priceChangedCount, 1, "Should update price for in stock item")
        
        XCTAssertEqual(cartStore.totalItemCount, 1)
        XCTAssertNil(cartStore.items["MED-OUT-OF-STOCK"])
        XCTAssertEqual(cartStore.items["MED-IN-STOCK"]?.product.price, 115.0)
        XCTAssertEqual(cartStore.subtotal, 115.0)
    }
}
