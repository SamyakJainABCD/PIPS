package com.example.pips

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Transaction(
    val id: String,
    val accountName: String,
    val amount: Double,
    val category: String,
    val details: String,
    val timestamp: Long
)

data class NotificationHistoryItem(
    val id: String,
    val notificationId: Int, // Added to cancel notification later
    val accountName: String,
    val messageBody: String,
    val amount: Double,
    var category: String? = null,
    val timestamp: Long,
    val isAuto: Boolean,
    var status: NotificationStatus,
    var isSeen: Boolean = false
)

enum class NotificationStatus {
    PENDING, CATEGORIZED, DISMISSED
}

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("pips_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CATEGORIES = "all_categories"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_REMEMBERED_CATEGORIES = "remembered_categories"
        private const val KEY_NOTIFICATION_HISTORY = "notification_history"
        private const val KEY_BUDGETS = "category_budgets"
        private const val KEY_BUDGET_ENABLED = "budget_enabled"
        private val DEFAULT_CATEGORIES = setOf("Food", "Shopping", "Bills", "Travel", "Health", "Education", "Entertainment", "Others")
    }

    fun getAllCategories(): List<String> {
        return sharedPreferences.getStringSet(KEY_CATEGORIES, DEFAULT_CATEGORIES)
            ?.toList() ?: DEFAULT_CATEGORIES.toList()
    }

    fun setAllCategories(categories: List<String>) {
        sharedPreferences.edit().putStringSet(KEY_CATEGORIES, categories.toSet()).apply()
    }

    /**
     * Saves a transaction. If a transaction with the same details (SMS body) already exists,
     * it updates the existing one to avoid duplicates when re-categorizing.
     */
    fun saveTransaction(transaction: Transaction) {
        val transactions = getTransactions().toMutableList()
        val existingIndex = transactions.indexOfFirst { it.details == transaction.details }
        
        if (existingIndex != -1) {
            val existing = transactions[existingIndex]
            // Update category, keep original ID and timestamp if we're just re-categorizing
            transactions[existingIndex] = transaction.copy(
                id = existing.id,
                timestamp = existing.timestamp
            )
        } else {
            transactions.add(transaction)
        }
        
        val json = gson.toJson(transactions)
        sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    fun getTransactions(): List<Transaction> {
        val json = sharedPreferences.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearTransactions() {
        sharedPreferences.edit().remove(KEY_TRANSACTIONS).apply()
    }

    fun rememberCategory(accountName: String, category: String) {
        val remembered = getRememberedCategories().toMutableMap()
        remembered[accountName] = category
        val json = gson.toJson(remembered)
        sharedPreferences.edit().putString(KEY_REMEMBERED_CATEGORIES, json).apply()
    }

    fun getRememberedCategory(accountName: String): String? {
        return getRememberedCategories()[accountName]
    }

    fun getRememberedCategories(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_REMEMBERED_CATEGORIES, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun removeRememberedCategory(accountName: String) {
        val remembered = getRememberedCategories().toMutableMap()
        remembered.remove(accountName)
        val json = gson.toJson(remembered)
        sharedPreferences.edit().putString(KEY_REMEMBERED_CATEGORIES, json).apply()
    }

    fun getNotificationHistory(): List<NotificationHistoryItem> {
        val json = sharedPreferences.getString(KEY_NOTIFICATION_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<NotificationHistoryItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveNotificationHistoryItem(item: NotificationHistoryItem) {
        val history = getNotificationHistory().toMutableList()
        val existingIndex = history.indexOfFirst { it.id == item.id }
        if (existingIndex != -1) {
            history[existingIndex] = item
        } else {
            history.add(item)
        }
        val json = gson.toJson(history)
        sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, json).apply()
    }

    fun updateNotificationStatus(notificationId: String, status: NotificationStatus, category: String? = null) {
        val history = getNotificationHistory().toMutableList()
        val index = history.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            history[index].status = status
            if (category != null) {
                history[index].category = category
            }
            val json = gson.toJson(history)
            sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, json).apply()
        }
    }

    fun markNotificationsAsSeen() {
        val history = getNotificationHistory().toMutableList()
        history.forEach { it.isSeen = true }
        val json = gson.toJson(history)
        sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, json).apply()
    }

    fun getMissedNotificationsCount(): Int {
        return getNotificationHistory().count { !it.isAuto && it.status == NotificationStatus.DISMISSED && !it.isSeen }
    }

    fun getBudgets(): Map<String, Double> {
        val json = sharedPreferences.getString(KEY_BUDGETS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Double>>() {}.type
        return gson.fromJson(json, type)
    }

    fun setBudget(category: String, amount: Double) {
        val budgets = getBudgets().toMutableMap()
        budgets[category] = amount
        val json = gson.toJson(budgets)
        sharedPreferences.edit().putString(KEY_BUDGETS, json).apply()
    }

    fun removeBudget(category: String) {
        val budgets = getBudgets().toMutableMap()
        budgets.remove(category)
        val json = gson.toJson(budgets)
        sharedPreferences.edit().putString(KEY_BUDGETS, json).apply()
    }

    fun isBudgetEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BUDGET_ENABLED, true)
    }

    fun setBudgetEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BUDGET_ENABLED, enabled).apply()
    }

    /**
     * Transfers all data associated with oldCategory to newCategory and deletes oldCategory.
     */
    fun transferAndDeleteCategory(oldCategory: String, newCategory: String) {
        // 1. Update Transactions
        val transactions = getTransactions().toMutableList()
        var transUpdated = false
        transactions.forEachIndexed { index, transaction ->
            if (transaction.category == oldCategory) {
                transactions[index] = transaction.copy(category = newCategory)
                transUpdated = true
            }
        }
        if (transUpdated) {
            val json = gson.toJson(transactions)
            sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
        }

        // 2. Update Remembered Categories (UPI Defaults)
        val remembered = getRememberedCategories().toMutableMap()
        var rememberedUpdated = false
        remembered.forEach { (upi, cat) ->
            if (cat == oldCategory) {
                remembered[upi] = newCategory
                rememberedUpdated = true
            }
        }
        if (rememberedUpdated) {
            val json = gson.toJson(remembered)
            sharedPreferences.edit().putString(KEY_REMEMBERED_CATEGORIES, json).apply()
        }

        // 3. Update Notification History
        val history = getNotificationHistory().toMutableList()
        var historyUpdated = false
        history.forEachIndexed { index, item ->
            if (item.category == oldCategory) {
                history[index].category = newCategory
                historyUpdated = true
            }
        }
        if (historyUpdated) {
            val json = gson.toJson(history)
            sharedPreferences.edit().putString(KEY_NOTIFICATION_HISTORY, json).apply()
        }

        // 4. Update Category List
        val categories = getAllCategories().toMutableList()
        categories.remove(oldCategory)
        setAllCategories(categories)

        // 5. Remove Budget for oldCategory (or transfer? The request says "transfer contents", 
        // usually refers to transactions, but let's keep it simple and just delete the old budget)
        removeBudget(oldCategory)
    }
}
