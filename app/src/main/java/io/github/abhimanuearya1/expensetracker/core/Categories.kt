package io.github.abhimanuearya1.expensetracker.core

/** Rules and starting data for classifications ("categories" in the code). */
object Categories {

    /** Shown for expenses whose classification was deleted or never set. */
    const val UNCATEGORIZED = "Uncategorized"

    const val MAX_NAME_LENGTH = 30

    /** Colour used for uncategorised expenses. */
    const val UNCATEGORIZED_COLOR: Int = 0xFF78909C.toInt()

    /** Colours offered for classifications. White text is readable on all of them. */
    val PALETTE: List<Int> = listOf(
        0xFFE53935, // red
        0xFFD81B60, // pink
        0xFF9C27B0, // purple
        0xFF673AB7, // deep purple
        0xFF3F51B5, // indigo
        0xFF1E88E5, // blue
        0xFF0097A7, // cyan
        0xFF00897B, // teal
        0xFF43A047, // green
        0xFF827717, // olive
        0xFFEF6C00, // orange
        0xFF795548, // brown
        0xFF546E7A, // blue grey
    ).map { it.toInt() }

    /** Names of the [PALETTE] colours, in the same order, for screen readers. */
    val PALETTE_NAMES: List<String> = listOf(
        "Red", "Pink", "Purple", "Deep purple", "Indigo", "Blue", "Cyan",
        "Teal", "Green", "Olive", "Orange", "Brown", "Blue grey",
    )

    data class Default(val name: String, val color: Int)

    /** Classifications created the first time the app starts. All can be renamed or deleted. */
    val DEFAULTS: List<Default> = listOf(
        Default("Food", 0xFFEF6C00.toInt()),
        Default("Groceries", 0xFF43A047.toInt()),
        Default("Transport", 0xFF1E88E5.toInt()),
        Default("Shopping", 0xFFD81B60.toInt()),
        Default("Bills", 0xFF3F51B5.toInt()),
        Default("Rent", 0xFF795548.toInt()),
        Default("Health", 0xFFE53935.toInt()),
        Default("Entertainment", 0xFF9C27B0.toInt()),
        Default("Education", 0xFF00897B.toInt()),
        Default("Travel", 0xFF0097A7.toInt()),
        Default("Other", 0xFF546E7A.toInt()),
    )

    /** Payment methods created the first time the app starts. All can be renamed or deleted. */
    val DEFAULT_PAYMENT_METHODS: List<Default> = listOf(
        Default("Cash", 0xFF43A047.toInt()),
        Default("UPI", 0xFF673AB7.toInt()),
        Default("Credit card", 0xFFE53935.toInt()),
        Default("Debit card", 0xFF1E88E5.toInt()),
        Default("Net banking", 0xFF00897B.toInt()),
    )

    /** Trims and collapses repeated spaces: "  Eating   out " -> "Eating out". */
    fun cleanName(raw: String): String =
        raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Returns a message to show the user if [raw] cannot be used as a
     * classification name, or null when it is fine.
     *
     * @param renamingFrom the current name when editing, so keeping the same name
     *   (or only changing its capitalisation) is allowed.
     */
    fun nameProblem(raw: String, existingNames: Collection<String>, renamingFrom: String? = null): String? {
        val name = cleanName(raw)
        if (name.isEmpty()) return "Enter a name"
        if (name.length > MAX_NAME_LENGTH) return "Use $MAX_NAME_LENGTH characters or fewer"
        if (name.equals(UNCATEGORIZED, ignoreCase = true)) return "This name is reserved"
        val taken = existingNames.any {
            it.equals(name, ignoreCase = true) && !it.equals(renamingFrom, ignoreCase = true)
        }
        if (taken) return "“$name” already exists"
        return null
    }

    /** First palette colour not in use, so a new classification looks different from the others. */
    fun nextColor(usedColors: Collection<Int>): Int =
        PALETTE.firstOrNull { it !in usedColors } ?: PALETTE[usedColors.size % PALETTE.size]
}
