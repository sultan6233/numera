package com.numerology.util

import org.postgresql.util.PGobject

/** Wrap a JSON string so the postgres driver stores it as jsonb. */
fun pgJsonb(json: String): PGobject = PGobject().apply {
    type = "jsonb"
    value = json
}
