package com.numerology.services

import com.numerology.models.ComputedNumbersDto
import com.numerology.models.ProfileResponse
import com.numerology.models.SaveProfileRequest
import com.numerology.repositories.ComputedNumbersRecord
import com.numerology.repositories.ComputedNumbersRepository
import com.numerology.repositories.UserRepository
import java.time.LocalDate
import java.util.UUID

class ProfileService(
    private val userRepository: UserRepository,
    private val computedNumbersRepository: ComputedNumbersRepository,
) {
    suspend fun saveProfile(userId: UUID, request: SaveProfileRequest): ProfileResponse {
        val birthDate = request.birthDate?.let { LocalDate.parse(it) }
        val user = userRepository.updateProfile(
            userId = userId,
            name = request.name,
            birthDate = birthDate,
            language = request.language,
            timezone = request.timezone,
        ) ?: error("User not found: $userId")

        val computed = request.computedNumbers?.let { dto ->
            computedNumbersRepository.upsert(
                ComputedNumbersRecord(
                    userId = userId,
                    lifePath = dto.lifePath,
                    expression = dto.expression,
                    soulUrge = dto.soulUrge,
                    personality = dto.personality,
                    birthDay = dto.birthDay,
                    healthCode = dto.healthCode,
                    businessCode = dto.businessCode,
                )
            )
        } ?: computedNumbersRepository.findByUserId(userId)

        return ProfileResponse(
            userId = user.id.toString(),
            name = user.name,
            birthDate = user.birthDate?.toString(),
            language = user.language,
            timezone = user.timezone,
            computedNumbers = computed?.let {
                ComputedNumbersDto(
                    lifePath = it.lifePath,
                    expression = it.expression,
                    soulUrge = it.soulUrge,
                    personality = it.personality,
                    birthDay = it.birthDay,
                    healthCode = it.healthCode,
                    businessCode = it.businessCode,
                )
            },
        )
    }

    suspend fun getProfile(userId: UUID): ProfileResponse {
        val user = userRepository.findById(userId) ?: error("User not found: $userId")
        val computed = computedNumbersRepository.findByUserId(userId)
        return ProfileResponse(
            userId = user.id.toString(),
            name = user.name,
            birthDate = user.birthDate?.toString(),
            language = user.language,
            timezone = user.timezone,
            computedNumbers = computed?.let {
                ComputedNumbersDto(
                    lifePath = it.lifePath,
                    expression = it.expression,
                    soulUrge = it.soulUrge,
                    personality = it.personality,
                    birthDay = it.birthDay,
                    healthCode = it.healthCode,
                    businessCode = it.businessCode,
                )
            },
        )
    }
}
