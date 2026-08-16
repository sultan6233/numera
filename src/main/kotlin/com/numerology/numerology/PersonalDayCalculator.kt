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
enum class FocusArea(val label: String, private val translations: Map<String, String>) {
    RELATIONSHIPS(
        "отношения",
        mapOf(
            "en" to "relationships", "es" to "relaciones", "pt-BR" to "relacionamentos",
            "uk" to "стосунки", "tr" to "ilişkiler", "de" to "Beziehungen",
            "fr" to "relations", "pl" to "relacje", "it" to "relazioni", "ru" to "отношения",
        ),
    ),
    ENERGY_WELLBEING(
        "энергия и самочувствие",
        mapOf(
            "en" to "energy & wellbeing", "es" to "energía y bienestar", "pt-BR" to "energia e bem-estar",
            "uk" to "енергія та самопочуття", "tr" to "enerji ve iyi oluş", "de" to "Energie & Wohlbefinden",
            "fr" to "énergie et bien-être", "pl" to "energia i samopoczucie", "it" to "energia e benessere", "ru" to "энергия и самочувствие",
        ),
    ),
    WORK_GOALS(
        "работа и цели",
        mapOf(
            "en" to "work & goals", "es" to "trabajo y metas", "pt-BR" to "trabalho e metas",
            "uk" to "робота та цілі", "tr" to "iş ve hedefler", "de" to "Arbeit & Ziele",
            "fr" to "travail et objectifs", "pl" to "praca i cele", "it" to "lavoro e obiettivi", "ru" to "работа и цели",
        ),
    ),
    CREATIVITY(
        "творчество и самовыражение",
        mapOf(
            "en" to "creativity & self-expression", "es" to "creatividad y autoexpresión", "pt-BR" to "criatividade e autoexpressão",
            "uk" to "творчість і самовираження", "tr" to "yaratıcılık ve kendini ifade", "de" to "Kreativität & Selbstausdruck",
            "fr" to "créativité et expression de soi", "pl" to "kreatywność i wyrażanie siebie", "it" to "creatività e autoespressione", "ru" to "творчество и самовыражение",
        ),
    ),
    INNER_GROWTH(
        "внутренний рост",
        mapOf(
            "en" to "inner growth", "es" to "crecimiento interior", "pt-BR" to "crescimento interior",
            "uk" to "внутрішнє зростання", "tr" to "iç gelişim", "de" to "inneres Wachstum",
            "fr" to "croissance intérieure", "pl" to "rozwój wewnętrzny", "it" to "crescita interiore", "ru" to "внутренний рост",
        ),
    );

    /** Localized label for `language` (a SupportedLanguages code), falling back to the Russian label. */
    fun labelFor(language: String?): String = translations[language] ?: label

    companion object {
        private val ordered = listOf(WORK_GOALS, RELATIONSHIPS, CREATIVITY, ENERGY_WELLBEING, INNER_GROWTH)

        /** Deterministic rotation by day-of-week so the theme doesn't repeat mechanically day to day. */
        fun forDate(date: LocalDate): FocusArea {
            val index = (date.dayOfWeek.value - 1) % ordered.size
            return ordered[index]
        }

        /** All five labels localized for `language`, in the LLM prompt's canonical order. */
        fun allLabelsFor(language: String?): List<String> = ordered.map { it.labelFor(language) }
    }
}
