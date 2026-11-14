package app.parivaar.panchang.engine.jyotish

/**
 * Ayanamsa definitions (docs/specs/M02.md section 3.2). Only LAHIRI and TRUE_CHITRA have constants in M02;
 * the other ids are placeholders that `Ayanamsa.isEnabled` reports as unavailable.
 */
enum class AyanamsaId { LAHIRI, TRUE_CHITRA, KP_NEW, RAMAN, YUKTESHWAR }

/** Which lunar node Rahu and Ketu follow: the osculating (true) node or the mean node. */
enum class NodeType { TRUE, MEAN }

/** House (bhava) division (docs/specs/M02.md section 3.3). */
enum class HouseSystem { WHOLE_SIGN, EQUAL, SRIPATI, PLACIDUS }

/** D2 rule set (docs/specs/M02.md section 3.7). KASHINATH has no table yet. */
enum class HoraVariant { PARASHARA, KASHINATH }

/** D3 rule set (docs/specs/M02.md section 3.7). */
enum class DrekkanaVariant { PARASHARA, JAGANNATH }

/**
 * Length of the dasha year (docs/specs/M02.md section 3.4).
 *
 * @property days days per dasha year
 */
@Suppress("MagicNumber") // each value is the definition of its year length
enum class DashaYear(val days: Double) {
    JULIAN_365_25(365.25),
    GREGORIAN_365_2425(365.2425),
    SAVANA_360(360.0),
}
