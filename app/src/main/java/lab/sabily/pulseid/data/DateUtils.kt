package lab.sabily.pulseid.data

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun parseFlexibleDate(dateStr: String?): Date? {
        if (dateStr == null) return null
        val formats = listOf(
            "yyyy-MM-dd",
            "d MMMM, yyyy",
            "dd MMMM, yyyy",
            "MMM dd, yyyy",
            "dd MMM, yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )
        val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply { timeZone = dhakaZone }
                val date = sdf.parse(dateStr)
                if (date != null) return date
            } catch (e: Exception) {
                // Continue to next format
            }
        }
        return null
    }

    fun parseFlexibleTime(timeStr: String?): Date? {
        if (timeStr == null) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "hh:mm a",
            "HH:mm:ss",
            "HH:mm"
        )
        val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply { timeZone = dhakaZone }
                val date = sdf.parse(timeStr)
                if (date != null) return date
            } catch (e: Exception) {
                // Continue to next format
            }
        }
        return null
    }
}
