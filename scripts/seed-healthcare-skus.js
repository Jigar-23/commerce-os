const fs = require('fs');
const { Pool } = require('pg');

// Read DATABASE_URL from .env
const dotenv = fs.readFileSync('.env', 'utf8');
let dbUrl = '';
dotenv.split('\n').forEach(line => {
  if (line.startsWith('DATABASE_URL=')) {
    dbUrl = line.substring('DATABASE_URL='.length).trim().replace(/^["']|["']$/g, '');
  }
});

if (!dbUrl) {
  console.error('DATABASE_URL not found in .env');
  process.exit(1);
}

const pool = new Pool({ connectionString: dbUrl, ssl: { rejectUnauthorized: false } });

const PRODUCTS = [
  // Existing SKU preserved & enriched
  {
    id: 'prod_60444',
    sku: 'para',
    name: 'Paracetamol 500mg Fast Action Tablets',
    brand_name: 'Apex Healthcare',
    pack_size: 'Strip of 10 Tablets',
    mrp: 35.00,
    price: 30.00,
    discounted_price: 30.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pain & Fever',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Paracetamol 500mg tablet for rapid fever reduction and mild headache/body ache relief.'
  },
  // Pain & Fever
  {
    id: 'prod_dolo_650',
    sku: 'MED_DOLO_650',
    name: 'Dolo 650mg Paracetamol Tablets',
    brand_name: 'Micro Labs',
    pack_size: 'Strip of 15 Tablets',
    mrp: 34.00,
    price: 30.00,
    discounted_price: 28.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pain & Fever',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Dolo 650 is an antipyretic and analgesic medicine containing paracetamol 650mg used to relieve fever and pain.'
  },
  {
    id: 'prod_crocin_500',
    sku: 'MED_CROCIN_500',
    name: 'Crocin Advance Fast Relief Tablets',
    brand_name: 'GlaxoSmithKline',
    pack_size: 'Strip of 20 Tablets',
    mrp: 32.00,
    price: 28.00,
    discounted_price: 25.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pain & Fever',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/045868/crocin-advance-500mg-tablet-20s-front-2-1753347026-non-watermark.jpg',
    description: 'Crocin Advance with Optizorb technology releases medicine 5x faster for headache and fever relief.'
  },
  {
    id: 'prod_combiflam',
    sku: 'MED_COMBIFLAM',
    name: 'Combiflam Pain Relief Tablets',
    brand_name: 'Sanofi India',
    pack_size: 'Strip of 20 Tablets',
    mrp: 48.00,
    price: 42.00,
    discounted_price: 38.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pain & Fever',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/045353/combiflam-strip-of-20-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Combiflam contains Ibuprofen (400mg) and Paracetamol (325mg) for joint pain, body ache, and fever.'
  },
  {
    id: 'prod_volini_gel',
    sku: 'MED_VOLINI_GEL',
    name: 'Volini Instant Pain Relief Gel 30g',
    brand_name: 'Sun Pharma',
    pack_size: 'Tube of 30g',
    mrp: 125.00,
    price: 110.00,
    discounted_price: 99.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pain & Fever',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/183157/volini-pain-relief-gel-tube-of-30g-2-1671741130.jpg',
    description: 'Volini Gel with Diclofenac deep penetration formula for muscle sprains, neck pain, and backache.'
  },
  // Cough & Cold
  {
    id: 'prod_benadryl_150',
    sku: 'MED_BENADRYL_150',
    name: 'Benadryl Cough Formula Syrup 150ml',
    brand_name: 'Johnson & Johnson',
    pack_size: 'Bottle of 150ml',
    mrp: 135.00,
    price: 120.00,
    discounted_price: 108.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Cough & Cold',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg',
    description: 'Benadryl Cough Formula provides fast relief from wet cough, chest congestion, and throat irritation.'
  },
  {
    id: 'prod_cheston_cold',
    sku: 'MED_CHESTON_COLD',
    name: 'Cheston Cold Strip of 10 Tablets',
    brand_name: 'Cipla',
    pack_size: 'Strip of 10 Tablets',
    mrp: 52.00,
    price: 45.00,
    discounted_price: 40.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Cough & Cold',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/037562/cheston-cold-strip-of-10-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Cheston Cold contains Cetirizine, Paracetamol, and Phenylephrine for running nose, sneezing, and fever.'
  },
  {
    id: 'prod_vicks_50g',
    sku: 'MED_VICKS_50G',
    name: 'Vicks VapoRub Relief Balm 50ml',
    brand_name: 'Procter & Gamble',
    pack_size: 'Tub of 50ml',
    mrp: 165.00,
    price: 150.00,
    discounted_price: 139.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Cough & Cold',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/181140/vicks-vaporub-50ml-relief-from-cold-cough-headache-and-body-pain-2-1671741126.jpg',
    description: 'Vicks VapoRub provides relief from 6 cough and cold symptoms with Menthol, Camphor, and Eucalyptus.'
  },
  {
    id: 'prod_otrivin_10ml',
    sku: 'MED_OTRIVIN_10ML',
    name: 'Otrivin Oxy Fast Relief Nasal Spray 10ml',
    brand_name: 'GlaxoSmithKline',
    pack_size: 'Bottle of 10ml',
    mrp: 112.00,
    price: 100.00,
    discounted_price: 89.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Cough & Cold',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/127452/otrivin-oxy-fast-relief-adult-nasal-spray-10ml-2-1671741098.jpg',
    description: 'Otrivin Adult Nasal Spray unblocks congested nose in 25 seconds with 8 hours of breathing relief.'
  },
  // Antibiotics
  {
    id: 'prod_augmentin_625',
    sku: 'MED_AUGMENTIN_625',
    name: 'Augmentin 625 Duo Tablets',
    brand_name: 'GlaxoSmithKline',
    pack_size: 'Strip of 10 Tablets',
    mrp: 204.00,
    price: 185.00,
    discounted_price: 169.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Antibiotics',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg',
    description: 'Amoxicillin (500mg) and Clavulanic Acid (125mg) broad-spectrum antibiotic for bacterial infections.'
  },
  {
    id: 'prod_azithral_500',
    sku: 'MED_AZITHRAL_500',
    name: 'Azithral 500mg Strip of 5 Tablets',
    brand_name: 'Alembic Pharma',
    pack_size: 'Strip of 5 Tablets',
    mrp: 122.00,
    price: 110.00,
    discounted_price: 99.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Antibiotics',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/018780/azithral-500mg-strip-of-5-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Azithromycin 500mg macrolide antibiotic for upper and lower respiratory tract infections.'
  },
  // Acidity & Digestion
  {
    id: 'prod_digene_mint',
    sku: 'MED_DIGENE_MINT',
    name: 'Digene Gel Acidity Relief Mint 200ml',
    brand_name: 'Abbott Healthcare',
    pack_size: 'Bottle of 200ml',
    mrp: 152.00,
    price: 135.00,
    discounted_price: 122.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Acidity & Digestion',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg',
    description: 'Doctor-recommended antacid gel for acidity, heartburn, and gas relief with pleasant mint flavor.'
  },
  {
    id: 'prod_pan_40',
    sku: 'MED_PAN_40',
    name: 'Pan 40mg Strip of 15 Tablets',
    brand_name: 'Alkem Laboratories',
    pack_size: 'Strip of 15 Tablets',
    mrp: 155.00,
    price: 138.00,
    discounted_price: 124.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Acidity & Digestion',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/128362/pan-40mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Pantoprazole 40mg proton pump inhibitor for severe gastroesophageal reflux disease and acid peptic ulcers.'
  },
  {
    id: 'prod_eno_regular',
    sku: 'MED_ENO_REGULAR',
    name: 'Eno Fruit Salt Regular 5g Pack of 6',
    brand_name: 'GlaxoSmithKline',
    pack_size: '6 Sachets x 5g',
    mrp: 60.00,
    price: 55.00,
    discounted_price: 49.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Acidity & Digestion',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/068112/eno-regular-fruit-salt-sachet-of-5g-2-1671741088.jpg',
    description: 'Eno gets to work in just 6 seconds against stomach acidity, fullness, and bloating.'
  },
  // First Aid & Wound
  {
    id: 'prod_dettol_250',
    sku: 'MED_DETTOL_250',
    name: 'Dettol Antiseptic Liquid 250ml',
    brand_name: 'Reckitt Benckiser',
    pack_size: 'Bottle of 250ml',
    mrp: 142.00,
    price: 130.00,
    discounted_price: 118.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'First Aid & Wound',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/054770/dettol-antiseptic-liquid-bottle-of-250-ml-2-1671741076.jpg',
    description: 'Trusted antiseptic liquid for first aid, cuts, scratches, personal hygiene, and surface disinfection.'
  },
  {
    id: 'prod_hansaplast_20',
    sku: 'MED_HANSAPLAST_20',
    name: 'Hansaplast Medicated Bandages Pack of 20',
    brand_name: 'Beiersdorf',
    pack_size: 'Pack of 20 Strips',
    mrp: 55.00,
    price: 50.00,
    discounted_price: 45.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'First Aid & Wound',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/084285/hansaplast-regular-bandage-jar-of-20-2-1671741090.jpg',
    description: 'Medicated adhesive wound plaster with strong adhesion and breathable fabric for minor cuts.'
  },
  {
    id: 'prod_electral_ors',
    sku: 'MED_ELECTRAL_ORS',
    name: 'Electral Oral Rehydration Salts Sachet 21.8g',
    brand_name: 'FDC Limited',
    pack_size: 'Pack of 4 Sachets',
    mrp: 88.00,
    price: 80.00,
    discounted_price: 72.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'First Aid & Wound',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg',
    description: 'WHO-formulated oral rehydration salts for rapid restoration of fluids and electrolytes lost in dehydration.'
  },
  // Diabetes Care
  {
    id: 'prod_glycomet_500',
    sku: 'MED_GLYCOMET_500',
    name: 'Glycomet 500mg Metformin Tablets',
    brand_name: 'USV Private Limited',
    pack_size: 'Strip of 20 Tablets',
    mrp: 46.00,
    price: 40.00,
    discounted_price: 35.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Diabetes Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg',
    description: 'Metformin Hydrochloride 500mg for effective management of type 2 diabetes mellitus.'
  },
  {
    id: 'prod_accuchek_50',
    sku: 'MED_ACCUCHEK_50',
    name: 'Accu-Chek Active Blood Glucose Strips 50s',
    brand_name: 'Roche Diabetes Care',
    pack_size: 'Box of 50 Strips',
    mrp: 1049.00,
    price: 950.00,
    discounted_price: 849.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Diabetes Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/000665/accu-chek-active-glucometer-test-strips-box-of-50-2-1671741050.jpg',
    description: 'Accurate blood glucose test strips compatible with Accu-Chek Active glucometer.'
  },
  // Heart & Blood Pressure
  {
    id: 'prod_telma_40',
    sku: 'MED_TELMA_40',
    name: 'Telma 40mg Telmisartan Tablets',
    brand_name: 'Glenmark Pharmaceuticals',
    pack_size: 'Strip of 30 Tablets',
    mrp: 235.00,
    price: 210.00,
    discounted_price: 188.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Heart & Blood Pressure',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg',
    description: 'Telmisartan 40mg angiotensin receptor blocker for treating essential hypertension and heart health.'
  },
  {
    id: 'prod_ecosprin_75',
    sku: 'MED_ECOSPRIN_75',
    name: 'Ecosprin 75mg Gastro-Resistant Tablets',
    brand_name: 'USV Private Limited',
    pack_size: 'Strip of 14 Tablets',
    mrp: 11.50,
    price: 10.00,
    discounted_price: 9.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Heart & Blood Pressure',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/064789/ecosprin-75mg-strip-of-14-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Low-dose aspirin 75mg antiplatelet medication to prevent blood clots and cardiac events.'
  },
  // Asthma & Inhalers
  {
    id: 'prod_asthalin_inh',
    sku: 'MED_ASTHALIN_INH',
    name: 'Asthalin 100mcg Inhaler 200 MDI',
    brand_name: 'Cipla',
    pack_size: 'Canister of 200 MDIs',
    mrp: 165.00,
    price: 150.00,
    discounted_price: 135.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Asthma & Inhalers',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/018151/asthalin-100mcg-inhaler-front-2-1753347026-non-watermark.jpg',
    description: 'Salbutamol 100mcg rescue bronchodilator for instant relief from asthma bronchospasms.'
  },
  // Thyroid Management
  {
    id: 'prod_thyronorm_50',
    sku: 'MED_THYRONORM_50',
    name: 'Thyronorm 50mcg Levothyroxine Tablets',
    brand_name: 'Abbott Healthcare',
    pack_size: 'Bottle of 120 Tablets',
    mrp: 195.00,
    price: 175.00,
    discounted_price: 159.00,
    rx_requirement: 'RX_REQUIRED',
    cold_chain_required: false,
    category: 'Thyroid Management',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/167733/thyronorm-50mcg-bottle-of-120-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Levothyroxine Sodium 50mcg for thyroid hormone replacement therapy in hypothyroidism.'
  },
  // Vitamins & Supplements
  {
    id: 'prod_becosules_z',
    sku: 'MED_BECOSULES_Z',
    name: 'Becosules Z Vitamin B-Complex with Zinc',
    brand_name: 'Pfizer Limited',
    pack_size: 'Strip of 20 Capsules',
    mrp: 52.00,
    price: 46.00,
    discounted_price: 39.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Vitamins & Supplements',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg',
    description: 'Vitamin B-Complex enriched with Vitamin C and Zinc to support daily energy, stamina, and tissue repair.'
  },
  {
    id: 'prod_limcee_500',
    sku: 'MED_LIMCEE_500',
    name: 'Limcee 500mg Vitamin C Chewable Tablets',
    brand_name: 'Abbott Healthcare',
    pack_size: 'Strip of 15 Tablets',
    mrp: 25.00,
    price: 22.00,
    discounted_price: 19.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Vitamins & Supplements',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/104523/limcee-500mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Orange-flavored Vitamin C (Ascorbic Acid) chewable tablets for collagen synthesis and immunity.'
  },
  // Calcium & Minerals
  {
    id: 'prod_shelcal_500',
    sku: 'MED_SHELCAL_500',
    name: 'Shelcal 500mg Calcium & Vitamin D3 Tablets',
    brand_name: 'Torrent Pharma',
    pack_size: 'Bottle of 30 Tablets',
    mrp: 138.00,
    price: 122.00,
    discounted_price: 109.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Calcium & Minerals',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/K78299/shelcal-500mg-bottle-of-30-tablets-6.1-1787223246.jpg',
    description: 'Calcium 500mg from natural oyster shells combined with Vitamin D3 250 IU for strong bones.'
  },
  // Immunity Boosters
  {
    id: 'prod_zincovit_tab',
    sku: 'MED_ZINCOVIT_TAB',
    name: 'Zincovit Multivitamin & Minerals Strip of 15',
    brand_name: 'Apex Laboratories',
    pack_size: 'Strip of 15 Tablets',
    mrp: 115.00,
    price: 102.00,
    discounted_price: 89.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Immunity Boosters',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/185850/zincovit-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg',
    description: 'Nutritional daily supplement with Grape Seed Extract, Zinc, and essential micronutrients.'
  },
  // Health Drinks & Protein
  {
    id: 'prod_protinex_400',
    sku: 'MED_PROTINEX_400',
    name: 'Protinex High Protein Drink Vanilla 400g',
    brand_name: 'Danone Nutricia',
    pack_size: 'Tin of 400g',
    mrp: 440.00,
    price: 399.00,
    discounted_price: 359.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Health Drinks & Protein',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/143520/protinex-original-flavour-tin-of-400-g-2-1671741106.jpg',
    description: 'Hydrolyzed peanut protein clinically proven to build muscle strength with 34% more protein.'
  },
  // Ayurveda - Chyawanprash
  {
    id: 'prod_dabur_chyawan',
    sku: 'MED_DABUR_CHYAWAN',
    name: 'Dabur Chyawanprash 2X Immunity 1kg',
    brand_name: 'Dabur India',
    pack_size: 'Jar of 1kg',
    mrp: 410.00,
    price: 370.00,
    discounted_price: 329.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Chyawanprash',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/054720/dabur-chyawanprash-2x-immunity-1kg-2-1671741076.jpg',
    description: 'Ayurvedic formula with over 40 natural herbs and Amla for 2x stamina and illness resistance.'
  },
  // Ayurveda - Pure Herbs
  {
    id: 'prod_himalaya_ashwa',
    sku: 'MED_HIMALAYA_ASHWA',
    name: 'Himalaya Ashwagandha Stress Relief 60s',
    brand_name: 'The Himalaya Drug Company',
    pack_size: 'Bottle of 60 Tablets',
    mrp: 220.00,
    price: 195.00,
    discounted_price: 175.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Pure Herbs',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/090410/himalaya-ashvagandha-general-wellness-tablets-bottle-of-60-2-1671741093.jpg',
    description: 'Pure organic Ashwagandha (Withania Somnifera) rejuvenates the body and lowers stress hormone levels.'
  },
  // Ayurveda - Herbal Juices
  {
    id: 'prod_baid_amla_1l',
    sku: 'MED_BAID_AMLA_1L',
    name: 'Baidyanath Pure Amla Juice 1 Litre',
    brand_name: 'Baidyanath',
    pack_size: 'Bottle of 1 Litre',
    mrp: 230.00,
    price: 205.00,
    discounted_price: 185.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Herbal Juices',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/021230/baidyanath-amla-juice-1-l-2-1671741062.jpg',
    description: '100% cold-pressed Indian Gooseberry juice packed with natural vitamin C for healthy digestion and glowing skin.'
  },
  // Ayurveda - Digestive Churn
  {
    id: 'prod_baid_triphala',
    sku: 'MED_BAID_TRIPHALA',
    name: 'Baidyanath Triphala Churna 500g Jar',
    brand_name: 'Baidyanath',
    pack_size: 'Jar of 500g',
    mrp: 175.00,
    price: 155.00,
    discounted_price: 139.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Digestive Churn',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/021310/baidyanath-triphala-churna-500g-2-1671741062.jpg',
    description: 'Traditional blend of Amla, Haritaki, and Bahera for natural gastrointestinal cleansing and digestive balance.'
  },
  // Personal Care - Dermatological Care
  {
    id: 'prod_cetaphil_125',
    sku: 'MED_CETAPHIL_125',
    name: 'Cetaphil Gentle Skin Cleanser 125ml',
    brand_name: 'Galderma India',
    pack_size: 'Bottle of 125ml',
    mrp: 333.00,
    price: 305.00,
    discounted_price: 279.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Dermatological Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/I40695/cetaphil-gentle-skin-cleanser-125ml-2-1671741005.jpg',
    description: 'Hydrating, soap-free, non-comedogenic daily cleanser formulated for sensitive and dry skin types.'
  },
  {
    id: 'prod_soframycin_30',
    sku: 'MED_SOFRAMYCIN_30',
    name: 'Soframycin Skin Antibacterial Cream 30g',
    brand_name: 'Sanofi India',
    pack_size: 'Tube of 30g',
    mrp: 68.00,
    price: 60.00,
    discounted_price: 54.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Dermatological Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/productsnowatermark/154780/soframycin-skin-cream-tube-of-30g-front-2-1753347026-non-watermark.jpg',
    description: 'Framycetin sulphate antibacterial cream for superficial skin infections, burns, scalds, and wounds.'
  },
  // Personal Care - Oral Care
  {
    id: 'prod_sensodyne_75',
    sku: 'MED_SENSODYNE_75',
    name: 'Sensodyne Fresh Gel Sensitivity Toothpaste 75g',
    brand_name: 'GlaxoSmithKline',
    pack_size: 'Tube of 75g',
    mrp: 160.00,
    price: 145.00,
    discounted_price: 130.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Oral Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/159840/sensodyne-fresh-gel-toothpaste-75g-2-1671741114.jpg',
    description: 'Dentist-recommended toothpaste providing daily relief from sensitivity and tooth cavity protection.'
  },
  // Personal Care - Baby & Infant Care
  {
    id: 'prod_sebamed_baby',
    sku: 'MED_SEBAMED_BABY',
    name: 'Sebamed Baby Cleansing Bar pH 5.5 100g',
    brand_name: 'USV Private Limited',
    pack_size: 'Bar of 100g',
    mrp: 240.00,
    price: 215.00,
    discounted_price: 195.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Baby & Infant Care',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/159115/sebamed-baby-cleansing-bar-100g-2-1671741113.jpg',
    description: '100% soap and alkali-free gentle baby cleansing bar with pH 5.5 to protect baby skin moisture barrier.'
  },
  // Personal Care - Antiseptics & Sanitizers
  {
    id: 'prod_lifebuoy_san',
    sku: 'MED_LIFEBUOY_SAN',
    name: 'Lifebuoy Total Hand Sanitizer Pump 500ml',
    brand_name: 'Hindustan Unilever',
    pack_size: 'Pump Bottle 500ml',
    mrp: 250.00,
    price: 220.00,
    discounted_price: 199.00,
    rx_requirement: 'OTC',
    cold_chain_required: false,
    category: 'Antiseptics & Sanitizers',
    image_url: 'https://cdn01.pharmeasy.in/dam/products_otc/105430/lifebuoy-total-hand-sanitizer-500ml-2-1671741095.jpg',
    description: 'Instant 70% alcohol-based hand sanitizer formula killing 99.99% germs without water in 10 seconds.'
  }
];

const TARGET_STORES = [
  'store_rewari_hub_01',
  'STORE_REWARI_01',
  'STORE_MASTER_001'
];

async function seed() {
  console.log('--- Starting Healthcare SKU & Inventory Migration ---');

  // 1. Upsert Products
  for (const p of PRODUCTS) {
    await pool.query(
      `INSERT INTO products (
        id, sku, name, brand_name, pack_size, mrp, price, discounted_price,
        rx_requirement, cold_chain_required, category, image_url, description, is_active
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, TRUE)
      ON CONFLICT (id) DO UPDATE SET
        sku = EXCLUDED.sku,
        name = EXCLUDED.name,
        brand_name = EXCLUDED.brand_name,
        pack_size = EXCLUDED.pack_size,
        mrp = EXCLUDED.mrp,
        price = EXCLUDED.price,
        discounted_price = EXCLUDED.discounted_price,
        rx_requirement = EXCLUDED.rx_requirement,
        cold_chain_required = EXCLUDED.cold_chain_required,
        category = EXCLUDED.category,
        image_url = EXCLUDED.image_url,
        description = EXCLUDED.description,
        is_active = TRUE,
        updated_at = NOW()`,
      [
        p.id, p.sku, p.name, p.brand_name, p.pack_size, p.mrp, p.price,
        p.discounted_price, p.rx_requirement, p.cold_chain_required,
        p.category, p.image_url, p.description
      ]
    );
  }
  console.log(`✅ Upserted ${PRODUCTS.length} authoritative products.`);

  // 2. Upsert Store Inventory & Audit Ledgers
  const INITIAL_STOCK = 50;
  for (const storeId of TARGET_STORES) {
    // Check if store exists
    const storeCheck = await pool.query('SELECT id, store_name FROM stores WHERE id = $1', [storeId]);
    if (storeCheck.rows.length === 0) {
      console.log(`⚠️ Store ${storeId} not found, skipping.`);
      continue;
    }

    for (const p of PRODUCTS) {
      // Upsert inventory row
      await pool.query(
        `INSERT INTO inventory (
          id, store_id, product_id, sku, product_name, stock_count, reserved_count, min_threshold
        ) VALUES (
          'inv_' || substr(md5(random()::text || clock_timestamp()::text), 1, 24),
          $1, $2, $3, $4, $5, 0, 5
        )
        ON CONFLICT (store_id, sku) DO UPDATE SET
          product_id = EXCLUDED.product_id,
          product_name = EXCLUDED.product_name,
          stock_count = GREATEST(inventory.stock_count, EXCLUDED.stock_count),
          min_threshold = 5,
          updated_at = NOW()`,
        [storeId, p.id, p.sku, p.name, INITIAL_STOCK]
      );

      // Insert ledger audit row if none exists
      const ledgerCheck = await pool.query(
        'SELECT id FROM inventory_ledger WHERE store_id = $1 AND sku = $2 AND reason = $3 LIMIT 1',
        [storeId, p.sku, 'INITIAL_STOCK_SEED']
      );
      if (ledgerCheck.rows.length === 0) {
        await pool.query(
          `INSERT INTO inventory_ledger (
            id, store_id, product_id, sku, delta, new_stock, reason, actor_id
          ) VALUES (
            'led_' || substr(md5(random()::text || clock_timestamp()::text), 1, 24),
            $1, $2, $3, $4, $5, $6, 'SYSTEM_SEEDED'
          )`,
          [storeId, p.id, p.sku, INITIAL_STOCK, INITIAL_STOCK, 'INITIAL_STOCK_SEED']
        );
      }
    }
    console.log(`✅ Seeded stock count of ${INITIAL_STOCK} for ${PRODUCTS.length} SKUs in store: ${storeId}`);
  }

  // 3. Verification Summary
  const countProds = await pool.query('SELECT count(*) FROM products WHERE is_active = TRUE');
  const countInv = await pool.query(
    'SELECT store_id, count(*) as sku_count, sum(stock_count) as total_stock, sum(reserved_count) as total_reserved FROM inventory GROUP BY store_id'
  );
  console.log(`\n🎉 SEED SUCCESSFUL! Total active products in catalog: ${countProds.rows[0].count}`);
  console.log('Store Inventory Breakdown:');
  console.table(countInv.rows);

  await pool.end();
}

seed().catch((err) => {
  console.error('❌ Seed failed:', err);
  process.exit(1);
});
