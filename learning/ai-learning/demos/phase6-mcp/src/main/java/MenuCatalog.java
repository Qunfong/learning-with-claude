import java.util.Map;

/**
 * Gedeelde "bron van waarheid" tussen {@link ReceiptGeneratorServer} en
 * {@link ReceiptAnalyticsServer} -- prijzen en BTW-tarief staan op ÉÉN plek,
 * niet gedupliceerd over twee servers die toevallig hetzelfde moeten weten.
 */
final class MenuCatalog {

    static final Map<String, Double> PRICES = Map.of(
            "koffie", 3.20,
            "cappuccino", 3.80,
            "broodje", 5.50,
            "boek", 14.99,
            "koptelefoon", 89.00,
            "muffin", 2.75);

    static final double VAT_RATE = 0.21; // NL BTW hoog tarief

    private MenuCatalog() {
    }
}
