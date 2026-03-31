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

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("pips_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_NOTIFICATION_CATEGORIES = "notification_categories"
        private const val KEY_EXTRA_CATEGORIES = "extra_categories"
        private const val KEY_TRANSACTIONS = "transactions"
        private val DEFAULT_NOTIFICATION_CATEGORIES = setOf("Food", "Shopping", "Bills")
        private val DEFAULT_EXTRA_CATEGORIES = setOf("Travel", "Health", "Education", "Entertainment", "Others")
    }

    fun getNotificationCategories(): List<String> {
        return sharedPreferences.getStringSet(KEY_NOTIFICATION_CATEGORIES, DEFAULT_NOTIFICATION_CATEGORIES)
            ?.toList() ?: DEFAULT_NOTIFICATION_CATEGORIES.toList()
    }

    fun setNotificationCategories(categories: List<String>) {
        sharedPreferences.edit().putStringSet(KEY_NOTIFICATION_CATEGORIES, categories.toSet()).apply()
    }

    fun getExtraCategories(): List<String> {
        return sharedPreferences.getStringSet(KEY_EXTRA_CATEGORIES, DEFAULT_EXTRA_CATEGORIES)
            ?.toList() ?: DEFAULT_EXTRA_CATEGORIES.toList()
    }

    fun setExtraCategories(categories: List<String>) {
        sharedPreferences.edit().putStringSet(KEY_EXTRA_CATEGORIES, categories.toSet()).apply()
    }

    fun getAllCategories(): List<String> {
        return (getNotificationCategories() + getExtraCategories()).distinct()
    }

    fun saveTransaction(transaction: Transaction) {
        val transactions = getTransactions().toMutableList()
        transactions.add(transaction)
        val json = gson.toJson(transactions)
        sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    fun getTransactions(): List<Transaction> {
        val json = sharedPreferences.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }
}
