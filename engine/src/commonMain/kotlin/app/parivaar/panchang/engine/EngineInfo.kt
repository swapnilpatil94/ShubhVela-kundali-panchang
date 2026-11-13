package app.parivaar.panchang.engine

/** Bootstrap marker for the pure-Kotlin engine. Real astronomy arrives in M01. */
object EngineInfo {
    const val MODULE_NAME: String = "engine"

    fun describe(): String = "parivaar-$MODULE_NAME"
}
