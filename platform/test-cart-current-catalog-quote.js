const assert = require('assert');
const { TransactionalCartRepository } = require('./repositories');

async function main() {
  const staleCartRow = {
    id: 'cart_item_1',
    productId: 'prod_1',
    sku: 'SKU_1',
    name: 'Old Item Name',
    quantity: 2,
    unitPrice: 100,
    price: 100,
    discountedPrice: 100,
    mrp: 120,
    coldChain: false,
    prescriptionRequired: false
  };

  const pool = {
    async query(sql) {
      if (sql.includes('SELECT items FROM carts')) {
        return { rows: [{ items: [staleCartRow] }] };
      }
      if (sql.includes('FROM products')) {
        return {
          rows: [{
            sku: 'SKU_1',
            id: 'prod_1',
            name: 'Current Item Name',
            price: '125.00',
            discounted_price: '110.00',
            mrp: '130.00',
            rx_requirement: 'SCHEDULE_H',
            cold_chain_required: true
          }]
        };
      }
      throw new Error(`Unexpected query: ${sql}`);
    }
  };

  const cart = await new TransactionalCartRepository(pool).getCart('cust_1');
  assert.strictEqual(cart.length, 1);
  assert.strictEqual(cart[0].unitPrice, 110);
  assert.strictEqual(cart[0].price, 110);
  assert.strictEqual(cart[0].discountedPrice, 110);
  assert.strictEqual(cart[0].mrp, 130);
  assert.strictEqual(cart[0].name, 'Current Item Name');
  assert.strictEqual(cart[0].prescriptionRequired, true);
  assert.strictEqual(cart[0].coldChain, true);
  console.log('PASS: cart quote refreshes price, prescription, and cold-chain fields from the active catalog');
}

main().catch(err => {
  console.error(err);
  process.exitCode = 1;
});
