package com.numerology.services

import com.numerology.models.CompanionRequest
import com.numerology.models.CompanionResponse
import com.numerology.repositories.CompanionRepository
import java.time.LocalDate
import java.util.UUID

class CompanionService(private val companionRepository: CompanionRepository) {

    suspend fun list(userId: UUID): List<CompanionResponse> =
        companionRepository.listForUser(userId).map {
            CompanionResponse(it.id.toString(), it.name, it.birthDate.toString(), it.relationLabel)
        }

    suspend fun create(userId: UUID, request: CompanionRequest): CompanionResponse {
        val created = companionRepository.create(userId, request.name, LocalDate.parse(request.birthDate), request.relationLabel)
        return CompanionResponse(created.id.toString(), created.name, created.birthDate.toString(), created.relationLabel)
    }

    suspend fun delete(userId: UUID, companionId: UUID): Boolean = companionRepository.delete(userId, companionId)
}
