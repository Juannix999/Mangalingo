package com.example.core

enum class SupportedLanguage(
    val code: String,
    val nativeName: String,
    val spanishName: String,
    val shortBadge: String,
    val mangaDexCodes: List<String>
) {
    SPANISH("es", "Español", "Español", "ES", listOf("es", "es-la")),
    ENGLISH("en", "English", "Inglés", "EN", listOf("en")),
    JAPANESE("ja", "日本語", "Japonés", "JA", listOf("ja")),
    FRENCH("fr", "Français", "Francés", "FR", listOf("fr")),
    PORTUGUESE("pt-br", "Português", "Portugués", "PT", listOf("pt-br", "pt")),
    KOREAN("ko", "한국어", "Coreano", "KO", listOf("ko")),
    ITALIAN("it", "Italiano", "Italiano", "IT", listOf("it")),
    GERMAN("de", "Deutsch", "Alemán", "DE", listOf("de"));

    companion object {
        fun fromCode(code: String?): SupportedLanguage {
            if (code.isNullOrBlank()) return ENGLISH
            val normalized = code.lowercase()
            return entries.firstOrNull {
                it.code == normalized || normalized in it.mangaDexCodes
            } ?: entries.firstOrNull {
                normalized.startsWith(it.code.substringBefore("-"))
            } ?: ENGLISH
        }

        fun displayBadge(code: String?): String {
            return when (code?.lowercase()) {
                "ja" -> "JA"
                "en" -> "EN"
                "es", "es-la" -> "ES"
                "fr" -> "FR"
                "pt", "pt-br" -> "PT"
                "ko" -> "KO"
                "it" -> "IT"
                "de" -> "DE"
                "zh", "zh-hk" -> "ZH"
                else -> code?.uppercase()?.take(4) ?: "??"
            }
        }

        fun displayName(code: String?): String {
            return when (code?.lowercase()) {
                "ja" -> "Japonés (日本語)"
                "en" -> "Inglés (English)"
                "es" -> "Español"
                "es-la" -> "Español (LA)"
                "fr" -> "Francés (Français)"
                "pt", "pt-br" -> "Portugués"
                "ko" -> "Coreano (한국어)"
                "it" -> "Italiano"
                "de" -> "Alemán (Deutsch)"
                "zh", "zh-hk" -> "Chino"
                else -> code?.uppercase() ?: "Desconocido"
            }
        }
    }
}
