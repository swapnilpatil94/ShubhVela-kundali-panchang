package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.share.bondcode.BondCode
import app.parivaar.panchang.core.share.bondcode.BondCodeException

/**
 * The Bond Code codec (core/share) as the Bonds port: any rejection becomes null (its messages never hold input bytes,
 * and nothing is logged). Links are accepted only for [host] (`https://<host>/b#<code>`).
 */
class BondCodeAdapter(private val host: String) : EngineBondEngine.BondCodePort {
    override fun encode(t: BondTraits): String? = try {
        BondCode.encode(t)
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun decode(input: String): BondTraits? = try {
        if (input.startsWith("https://", ignoreCase = true)) {
            BondCode.fromLinkOrCode(input, host)
        } else {
            BondCode.decode(input)
        }
    } catch (_: BondCodeException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
