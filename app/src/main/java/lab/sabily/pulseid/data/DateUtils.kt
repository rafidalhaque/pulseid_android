package lab.sabily.pulseid.data

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun parseFlexibleDate(dateStr: String?, tz: String = "Asia/Dhaka"): Date? {
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
        val timeZone = TimeZone.getTimeZone(tz)
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply { this.timeZone = timeZone }
                val date = sdf.parse(dateStr)
                if (date != null) return date
            } catch (e: Exception) {
                // Continue to next format
            }
        }
        return null
    }

    fun parseFlexibleTime(timeStr: String?, tz: String = "Asia/Dhaka"): Date? {
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
        val timeZone = TimeZone.getTimeZone(tz)
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply { this.timeZone = timeZone }
                val date = sdf.parse(timeStr)
                if (date != null) return date
            } catch (e: Exception) {
                // Continue to next format
            }
        }
        return null
    }

    fun formatUptime(uptimeMs: Long): String {
        val seconds = (uptimeMs / 1000) % 60
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val hours = (uptimeMs / (1000 * 60 * 60))
        
        val result = StringBuilder()
        if (hours > 0) result.append("${hours}h ")
        if (minutes > 0 || hours > 0) result.append("${minutes}m ")
        result.append("${seconds}s")
        
        return result.toString()
    }
}
