import Foundation

/// Universal resolver mapping product SKUs and keywords to authentic packaging photography.
/// Mirrors Android's `MedicineImageResolver.kt` for exact visual parity.
public struct MedicineImageResolver {
    public static let packagingPhotos: [String: String] = [
        "SKU-PCM-650": "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
        "SKU-PARA-500": "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg",
        "SKU-AMOX-625": "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
        "SKU-COLD-01": "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
        "SKU-INS-01": "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg",
        "SKU-PARACIP-500": "https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg",
        "SKU-GLYC-500": "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
        "SKU-CET-10": "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg",
        "SKU-AZI-500": "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg"
    ]

    public static let keywordPhotos: [(String, String)] = [
        ("dolo", "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"),
        ("paracetamol", "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg"),
        ("crocin", "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg"),
        ("amoxiclav", "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"),
        ("augmentin", "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"),
        ("cold", "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"),
        ("cough", "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"),
        ("benadryl", "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"),
        ("insulin", "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg"),
        ("lantus", "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg"),
        ("paracip", "https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg"),
        ("glycomet", "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg"),
        ("cetrizet", "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg"),
        ("cetzine", "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg"),
        ("azithral", "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg"),
        ("azithromycin", "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg"),
        ("pan 40", "https://cdn01.pharmeasy.in/dam/productsnowatermark/I00306/pan-40mg-strip-of-15-tablets-front-2-1756099995-non-watermarked.jpg"),
        ("shelcal", "https://cdn01.pharmeasy.in/dam/products_otc/K78299/shelcal-500mg-bottle-of-30-tablets-6.1-1787223246.jpg"),
        ("becosules", "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"),
        ("volini", "https://cdn01.pharmeasy.in/dam/products_otc/I00392/volini-pain-relief-gel-tube-of-100-g-6.1-1712725504.jpg"),
        ("digene", "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg")
    ]

    public static func resolve(sku: String? = nil, name: String? = nil, rawImage: String? = nil) -> String {
        if let raw = rawImage, !raw.isEmpty, raw.hasPrefix("http"), !raw.contains("unsplash.com") {
            return raw
        }
        if let s = sku?.uppercased(), let photo = packagingPhotos[s] {
            return photo
        }
        if let n = name?.lowercased() {
            for (kw, url) in keywordPhotos {
                if n.contains(kw) {
                    return url
                }
            }
        }
        if let raw = rawImage, !raw.isEmpty, raw.hasPrefix("http") {
            return raw
        }
        return ""
    }
}
