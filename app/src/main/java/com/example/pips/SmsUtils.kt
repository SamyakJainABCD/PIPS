package com.example.pips

import java.util.regex.Pattern

object SmsUtils {
    fun isUpiMessage(message: String): Boolean {
        val lowerCaseMessage = message.lowercase()
        return lowerCaseMessage.contains("upi") && 
               (lowerCaseMessage.contains("ref no") || lowerCaseMessage.contains("vpa") || lowerCaseMessage.contains("transid"))
    }

    fun extractAccountName(message: String): String {
        val pattern = Pattern.compile("(?i)(?:to|from)\\s+([^\\s]+(?:\\s+[^\\s]+){0,2})", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1) ?: "Unknown"
        } else {
            "UPI Transaction"
        }
    }

    fun extractAmount(message: String): Double {
        val pattern = Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)")
        val matcher = pattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }
}
