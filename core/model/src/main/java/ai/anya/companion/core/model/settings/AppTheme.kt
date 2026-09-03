package ai.anya.companion.core.model.settings

/** In-app color scheme. `System` follows the phone light/dark setting. */
public enum class AppTheme {
    System,
    Light,
    Dark,
    ;

    public val stored: String
        get() = when (this) {
            System -> "system"
            Light -> "light"
            Dark -> "dark"
        }

    public fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    public companion object {
        public fun fromStored(value: String?): AppTheme = when (value) {
            "light" -> Light
            "dark" -> Dark
            else -> System
        }
    }
}
