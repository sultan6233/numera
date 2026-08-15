package com.numerology.numerology

import java.time.LocalDate

/**
 * Personal Day Number: digit sum of (birth day + birth month + current day +
 * current month + current year), reduced to a single digit 1-9 — no master
 * numbers for this particular figure, per the spec (§5).
 */
object PersonalDayCalculator {

    fun calculate(birthDate: LocalDate, today: LocalDate): Int {
        val sum = birthDate.dayOfMonth + birthDate.monthValue + today.dayOfMonth + today.monthValue + today.year
        return reduceToSingleDigit(sum)
    }

    private fun reduceToSingleDigit(number: Int): Int {
        var n = number
        while (n > 9) {
            n = n.toString().sumOf { it.digitToInt() }
        }
        return if (n == 0) 9 else n
    }
}

/** The five focus themes the spec rotates through, deterministically by day of week. */
enum class FocusArea(val label: String) {
    RELATIONSHIPS("отношения"),
    ENERGY_WELLBEING("энергия и самочувствие"),
    WORK_GOALS("работа и цели"),
    CREATIVITY("творчество и самовыражение"),
    INNER_GROWTH("внутренний рост");

    companion object {
        private val ordered = listOf(WORK_GOALS, RELATIONSHIPS, CREATIVITY, ENERGY_WELLBEING, INNER_GROWTH)

        /** Deterministic rotation by day-of-week so the theme doesn't repeat mechanically day to day. */
        fun forDate(date: LocalDate): FocusArea {
            val index = (date.dayOfWeek.value - 1) % ordered.size
            return ordered[index]
        }
    }
}
