package com.numerology.services

import com.numerology.repositories.RemoteConfigRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class RemoteConfigService(private val remoteConfigRepository: RemoteConfigRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    /** GET /config: merges every remote_config row into one JSON object clients can cache locally. */
    suspend fun getConfig(): JsonObject {
        val entries = remoteConfigRepository.getAll()
        return buildJsonObject {
            put("version", entries.maxOfOrNull { it.version } ?: 1)
            for (entry in entries) {
                put(entry.key, json.parseToJsonElement(entry.valueJson).jsonObject)
            }
        }
    }

    suspend fun updateConfig(key: String, valueJson: String) {
        // Validate it's well-formed JSON before writing.
        json.parseToJsonElement(valueJson)
        remoteConfigRepository.upsert(key, valueJson)
    }
}
