package com.example.pips

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions

class TransactionClassifier public constructor(context: Context) {
    private var textEmbedder: TextEmbedder? = null
    private val TAG = "TransactionClassifier"

    // Cache for category embeddings
    private var cachedCategories: List<String>? = null
    private var cachedEmbeddings: List<Embedding?>? = null

    // Keyword map for common Indian UPI transactions
    private val keywordMap = mapOf(
        "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "eat", "bakery", "food", "dining", "hotel", "instamart", "blinkit", "bigbasket", "pizza", "burger", "coffee", "starbucks", "chai"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "shop", "store", "mall", "fashion", "ajio", "meesho", "nykaa", "reliance", "dmart", "max ", "zara", "h&m", "retail"),
        "Travel" to listOf("uber", "ola", "taxi", "metro", "irctc", "flight", "bus", "petrol", "fuel", "shell", "hpcl", "bpcl", "recharge", "fastag", "makemytrip", "goibibo", "rapido", "auto"),
        "Bills" to listOf("electricity", "water", "gas", "recharge", "mobile", "broadband", "insurance", "bill", "utility", "bescom", "airtel", "jio", "vi ", "tata sky", "dth", "rent"),
        "Health" to listOf("hospital", "pharmacy", "medical", "doctor", "lab", "clinic", "health", "apollo", "pharmeasy", "1mg", "medplus", "dental"),
        "Education" to listOf("school", "college", "university", "fees", "course", "education", "udemy", "coursera", "byjus", "tuition"),
        "Entertainment" to listOf("movie", "cinema", "pvr", "netflix", "prime", "spotify", "game", "entertainment", "bookmyshow", "inox", "youtube")
    )

    companion object {
        @Volatile
        private var instance: TransactionClassifier? = null

        fun getInstance(context: Context): TransactionClassifier {
            return instance ?: synchronized(this) {
                instance ?: TransactionClassifier(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("mobilebert_embedding.tflite")
                .build()

            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe TextEmbedder initialized successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe embedding model not found: ${e.message}")
        }
    }

    /**
     * Updates the category embedding cache if the list of categories has changed.
     */
    private fun updateCacheIfNeeded(allCategories: List<String>) {
        if (cachedCategories == allCategories && cachedEmbeddings != null) {
            return
        }

        Log.d(TAG, "Updating category embeddings cache for ${allCategories.size} categories.")
        textEmbedder?.let { embedder ->
            try {
                cachedEmbeddings = allCategories.map { category ->
                    embedder.embed(category).embeddingResult().embeddings().firstOrNull()
                }
                cachedCategories = allCategories.toList()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating category cache", e)
            }
        }
    }

    fun classify(text: String, allCategories: List<String>): List<String> {
        val lowerText = text.lowercase()
        val suggestions = mutableListOf<String>()

        updateCacheIfNeeded(allCategories)

        // 1. Semantic Similarity using Cached Embeddings
        textEmbedder?.let { embedder ->
            try {
                val textResult = embedder.embed(text)
                val textEmbedding = textResult.embeddingResult().embeddings().firstOrNull()

                if (textEmbedding != null && cachedEmbeddings != null) {
                    val scores = allCategories.indices.map { i ->
                        val category = allCategories[i]
                        val categoryEmbedding = cachedEmbeddings!![i]
                        val similarity = if (categoryEmbedding != null) {
                            TextEmbedder.cosineSimilarity(textEmbedding, categoryEmbedding)
                        } else 0.0
                        category to similarity
                    }

                    val semanticMatches = scores
                        .filter { it.second > 0.3 }
                        .sortedByDescending { it.second }
                        .map { it.first }
                    
                    suggestions.addAll(semanticMatches)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Embedding error", e)
            }
        }

        // 2. Keyword boost
        keywordMap.forEach { (category, keywords) ->
            if (keywords.any { lowerText.contains(it) }) {
                val matched = allCategories.find { it.equals(category, ignoreCase = true) }
                if (matched != null) {
                    suggestions.remove(matched)
                    suggestions.add(0, matched)
                }
            }
        }

        // 3. Name match
        allCategories.forEach { category ->
            if (category.length > 3 && lowerText.contains(category.lowercase()) && !suggestions.contains(category)) {
                suggestions.add(0, category)
            }
        }

        val finalSuggestions = suggestions.distinct().take(3).toMutableList()

        var i = 0
        while (finalSuggestions.size < 3 && i < allCategories.size) {
            if (!finalSuggestions.contains(allCategories[i])) {
                finalSuggestions.add(allCategories[i])
            }
            i++
        }

        return finalSuggestions
    }

    fun close() {
        textEmbedder?.close()
        textEmbedder = null
    }
}
