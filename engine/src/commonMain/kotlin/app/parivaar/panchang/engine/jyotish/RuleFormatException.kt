package app.parivaar.panchang.engine.jyotish

/**
 * A rule file is malformed (docs/specs/M02.md section 2). The message is the [code] and the JSON [path] only,
 * never a value from the file.
 *
 * @property code stable error id such as MISSING_FIELD or OUT_OF_RANGE
 * @property path JSON path of the offending element, e.g. `$.rules[3].when`
 */
class RuleFormatException(val code: String, val path: String) : IllegalArgumentException("$code $path")
