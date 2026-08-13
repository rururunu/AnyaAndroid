package ai.anya.companion.core.model.settings

/** In-app UI language. `System` follows the phone locale, falling back to English. */
public enum class AppLanguage {
    System,
    English,
    Chinese,
    ;

    public val stored: String
        get() = when (this) {
            System -> "system"
            English -> "en"
            Chinese -> "zh"
        }

    public companion object {
        public fun fromStored(value: String?): AppLanguage = when (value) {
            "en" -> English
            "zh" -> Chinese
            else -> System
        }
    }
}
