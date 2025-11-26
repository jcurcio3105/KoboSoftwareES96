package com.example.individualcounter

/**
 * Data class representing a single batch entry (row)
 */
data class BatchEntry(
    val c1: Int,
    val c2: Int,
    val c3: Int,
    val timestamp: Long
) {
    companion object {

        /**
         * Parses a CSV line into a BatchEntry.
         * Will try to extract the first 4 numeric values in order.
         * Returns null if it cannot find 4 numeric values.
         */
        fun fromCSVLine(line: String): BatchEntry? {
            val parts = line.split(",")
            if (parts.size < 4) return null

            return try {
                val c1 = parts[0].toInt()
                val c2 = parts[1].toInt()
                val c3 = parts[2].toInt()
                val ts = parts[3].toLong() // only take the Arduino timestamp

                // Use phone timestamp instead
                BatchEntry(c1, c2, c3, System.currentTimeMillis())
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts this BatchEntry into a CSV line.
     */
    fun toCSVLine(): String = "$c1,$c2,$c3,$timestamp"
}



