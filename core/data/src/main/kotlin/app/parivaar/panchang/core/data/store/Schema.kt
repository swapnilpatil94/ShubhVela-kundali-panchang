package app.parivaar.panchang.core.data.store

/** Database layout. Only table, random id, order and payload version are in clear; everything else is in the blob. */
internal object Schema {
    const val VERSION = 1
    const val DB_NAME = "pp_family.db"
    const val TABLE = "record"

    /** Must equal `core/data/schemas/v1.sql` (checked by SchemaTest). */
    const val CREATE_V1 =
        "CREATE TABLE record(tbl INTEGER NOT NULL, id BLOB NOT NULL, pos INTEGER NOT NULL DEFAULT 0, " +
            "ver INTEGER NOT NULL, blob BLOB NOT NULL, PRIMARY KEY(tbl, id)) WITHOUT ROWID;"

    /** Key n holds the statements that upgrade version n to n + 1. Empty while VERSION == 1. */
    val MIGRATIONS: Map<Int, List<String>> = emptyMap()
}
