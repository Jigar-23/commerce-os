INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_para_500', 'SKU-PARA-500', 'Paracetamol 500mg IP', 'Cipla', '15 tablets', 8, 5, 5, 'OTC', false, 'Pain & Fever', 'https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_para_500', 'store_rewari_hub_01', 'prod_para_500', 'SKU-PARA-500', 'Paracetamol 500mg IP', 51, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_amox_625', 'SKU-AMOX-625', 'Amoxiclav 625 Duo', 'GSK', '10 tablets', 220, 170, 170, 'RX', false, 'Antibiotics', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_amox_625', 'store_rewari_hub_01', 'prod_amox_625', 'SKU-AMOX-625', 'Amoxiclav 625 Duo', 37, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_cold_01', 'SKU-COLD-01', 'Cold & Cough Relief Syrup', 'Himalaya', '100ml', 110, 85, 85, 'OTC', false, 'Cold & Cough', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_cold_01', 'store_rewari_hub_01', 'prod_cold_01', 'SKU-COLD-01', 'Cold & Cough Relief Syrup', 59, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_insulin', 'SKU-INS-01', 'Insulin Glargine Pen', 'Sanofi', '1 pen (3ml)', 680, 600, 600, 'RX', true, 'Diabetes Care', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_insulin', 'store_rewari_hub_01', 'prod_insulin', 'SKU-INS-01', 'Insulin Glargine Pen', 20, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_paracip', 'SKU-PARACIP-500', 'Paracip 500', 'Mankind', '15 tablets', 6, 4, 4, 'OTC', false, 'Pain & Fever', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_paracip', 'store_rewari_hub_01', 'prod_paracip', 'SKU-PARACIP-500', 'Paracip 500', 210, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_glycomet', 'SKU-GLYC-500', 'Glycomet 500', 'USV', '10 tablets', 45, 33, 33, 'RX', false, 'Diabetes Care', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_glycomet', 'store_rewari_hub_01', 'prod_glycomet', 'SKU-GLYC-500', 'Glycomet 500', 88, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_cetrizet', 'SKU-CET-10', 'Cetrizet 10', 'Sun Pharma', '10 tablets', 52, 36, 36, 'OTC', false, 'Cold & Cough', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_cetrizet', 'store_rewari_hub_01', 'prod_cetrizet', 'SKU-CET-10', 'Cetrizet 10', 140, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('prod_azithral', 'SKU-AZI-500', 'Azithral 500', 'Aristo', '5 tablets', 150, 128, 128, 'RX', false, 'Antibiotics', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_prod_azithral', 'store_rewari_hub_01', 'prod_azithral', 'SKU-AZI-500', 'Azithral 500', 64, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

INSERT INTO products (id, sku, name, brand_name, pack_size, mrp, price, discounted_price, rx_requirement, cold_chain_required, category, image_url, store_id, is_active, created_at, updated_at)
VALUES ('med_001', 'SKU-PCM-650', 'Dolo 650mg Paracetamol Tablet', 'Micro Labs', '15 tablets', 34, 30.5, 30.5, 'OTC', false, 'Pain & Fever', 'https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg', 'store_rewari_hub_01', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, mrp = EXCLUDED.mrp, price = EXCLUDED.price, discounted_price = EXCLUDED.discounted_price, category = EXCLUDED.category, image_url = EXCLUDED.image_url, store_id = EXCLUDED.store_id;

INSERT INTO inventory (id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold, updated_at)
VALUES ('inv_med_001', 'store_rewari_hub_01', 'med_001', 'SKU-PCM-650', 'Dolo 650mg Paracetamol Tablet', 499, 0, 5, NOW())
ON CONFLICT (store_id, sku) DO UPDATE SET stock_count = EXCLUDED.stock_count, product_name = EXCLUDED.product_name;

