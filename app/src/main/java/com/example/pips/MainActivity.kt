package com.example.pips

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pips.ui.theme.PIPSTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private val transactionDetailsState = mutableStateOf<String?>(null)
    private val accountNameState = mutableStateOf<String?>(null)
    private val notificationUuidState = mutableStateOf<String?>(null)
    private val notificationIdState = mutableStateOf<Int?>(null)
    private val initialCategoryState = mutableStateOf<String?>(null)
    private val openUpiSettingsState = mutableStateOf<String?>(null)
    private val refreshTrigger = mutableStateOf(0)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTrigger.value += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter("com.example.pips.UPDATE_TRANSACTIONS"), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter("com.example.pips.UPDATE_TRANSACTIONS"))
        }

        setContent {
            PIPSTheme {
                val transactionDetails by transactionDetailsState
                val accountName by accountNameState
                val notificationUuid by notificationUuidState
                val notificationId by notificationIdState
                val initialCategory by initialCategoryState
                val openUpiSettings by openUpiSettingsState
                val refresh by refreshTrigger
                var currentScreen by remember { mutableStateOf("summary") }
                var settingsSubScreen by remember { mutableStateOf<String?>(null) }
                var selectedCategoryForHistory by remember { mutableStateOf<String?>(null) }

                val context = LocalContext.current
                val prefs = remember { PreferencesManager(context) }
                val missedCount = remember(refresh) { prefs.getMissedNotificationsCount() }

                LaunchedEffect(openUpiSettings) {
                    if (openUpiSettings != null) {
                        currentScreen = "settings"
                        settingsSubScreen = "upi_defaults"
                    }
                }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            shadowElevation = 3.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentScreen != "summary") {
                                    IconButton(onClick = { 
                                        if (settingsSubScreen != null) {
                                            settingsSubScreen = null
                                        } else {
                                            currentScreen = "summary"
                                            openUpiSettingsState.value = null
                                        }
                                    }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(48.dp))
                                }

                                Text(
                                    text = if (settingsSubScreen != null) settingsSubScreen!!.replace("_", " ").replaceFirstChar { it.uppercase() } else "PIPS - UPI Tracker",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )

                                if (currentScreen == "summary") {
                                    BadgedBox(
                                        badge = {
                                            if (missedCount > 0) {
                                                Badge { Text(missedCount.toString()) }
                                            }
                                        }
                                    ) {
                                        IconButton(onClick = { currentScreen = "notifications" }) {
                                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                        }
                                    }
                                    IconButton(onClick = { currentScreen = "settings" }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(48.dp))
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        Column {
                            PermissionScreen()
                            
                            when (currentScreen) {
                                "summary" -> CategorySummaryScreen(
                                    refreshTrigger = refresh,
                                    onCategoryClick = { selectedCategoryForHistory = it }
                                )
                                "settings" -> SettingsScreen(
                                    subScreen = settingsSubScreen,
                                    onNavigateToSub = { settingsSubScreen = it },
                                    initialUpiToEdit = openUpiSettings,
                                    refreshTrigger = refresh,
                                    onRefresh = { refreshTrigger.value += 1 }
                                )
                                "notifications" -> NotificationHistoryScreen(
                                    refreshTrigger = refresh,
                                    onItemClick = { item ->
                                        transactionDetailsState.value = item.messageBody
                                        accountNameState.value = item.accountName
                                        notificationUuidState.value = item.id
                                        notificationIdState.value = item.notificationId
                                        initialCategoryState.value = item.category
                                    }
                                )
                            }
                        }

                        if (transactionDetails != null) {
                            TransactionCategorizationDialog(
                                accountName = accountName ?: "Unknown",
                                details = transactionDetails!!,
                                notificationUuid = notificationUuid,
                                notificationId = notificationId,
                                initialCategory = initialCategory,
                                onDismiss = { 
                                    transactionDetailsState.value = null
                                    accountNameState.value = null
                                    notificationUuidState.value = null
                                    notificationIdState.value = null
                                    initialCategoryState.value = null
                                    refreshTrigger.value += 1
                                }
                            )
                        }

                        selectedCategoryForHistory?.let { category ->
                            CategoryHistoryDialog(
                                category = category,
                                refreshTrigger = refresh,
                                onDismiss = { selectedCategoryForHistory = null }
                            )
                        }
                    }
                }
                
                if (settingsSubScreen != null) {
                    BackHandler {
                        settingsSubScreen = null
                    }
                } else if (currentScreen != "summary") {
                    BackHandler {
                        currentScreen = "summary"
                        openUpiSettingsState.value = null
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.hasExtra("transaction_details")) {
                transactionDetailsState.value = it.getStringExtra("transaction_details")
                accountNameState.value = it.getStringExtra("account_name")
                notificationUuidState.value = it.getStringExtra("notification_uuid")
                if (it.hasExtra("notification_id")) {
                    notificationIdState.value = it.getIntExtra("notification_id", -1)
                }
            }
            if (it.hasExtra("open_upi_settings")) {
                openUpiSettingsState.value = it.getStringExtra("open_upi_settings")
            }
        }
    }
}

@Composable
fun CategorySummaryScreen(refreshTrigger: Int, onCategoryClick: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    
    val transactions = remember(refreshTrigger) { prefs.getTransactions() }
    val categories = remember(refreshTrigger) { prefs.getAllCategories() }
    val budgets = remember(refreshTrigger) { prefs.getBudgets() }
    val budgetEnabled = remember(refreshTrigger) { prefs.isBudgetEnabled() }

    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)

    val currentMonthTransactions = transactions.filter {
        val transCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        transCal.get(Calendar.MONTH) == currentMonth && transCal.get(Calendar.YEAR) == currentYear
    }

    val categoryTotals = categories.associateWith { category ->
        currentMonthTransactions.filter { it.category == category }.sumOf { it.amount }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                if (budgetEnabled) "Monthly Budget Summary" else "Monthly Spending Summary",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(categories) { category ->
            val total = categoryTotals[category] ?: 0.0
            val budget = if (budgetEnabled) budgets[category] ?: 0.0 else 0.0
            val isOverBudget = budget > 0 && total > budget
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onCategoryClick(category) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isOverBudget) MaterialTheme.colorScheme.errorContainer 
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "₹${String.format("%.2f", total)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (budget > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (total / budget).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Budget: ₹${String.format("%.2f", budget)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (isOverBudget) {
                                Text(
                                    "Over by ₹${String.format("%.2f", total - budget)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    "Left: ₹${String.format("%.2f", budget - total)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else if (budgetEnabled) {
                        Text(
                            "No budget set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationHistoryScreen(refreshTrigger: Int, onItemClick: (NotificationHistoryItem) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val history = remember(refreshTrigger) { prefs.getNotificationHistory().sortedByDescending { it.timestamp } }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        prefs.markNotificationsAsSeen()
        context.sendBroadcast(Intent("com.example.pips.UPDATE_TRANSACTIONS"))
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "Notification History",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        if (history.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No notification history found.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(history) { item ->
                val isMissed = !item.isAuto && item.status == NotificationStatus.DISMISSED
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onItemClick(item) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMissed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.accountName, fontWeight = FontWeight.Bold)
                            Text("₹${String.format("%.2f", item.amount)}", color = MaterialTheme.colorScheme.primary)
                        }
                        Text(item.messageBody, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val statusText = when (item.status) {
                                NotificationStatus.PENDING -> "Action Required"
                                NotificationStatus.CATEGORIZED -> "Categorized: ${item.category}"
                                NotificationStatus.DISMISSED -> if (item.isAuto) "Auto-handled" else "Missed (Dismissed)"
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                dateFormat.format(Date(item.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHistoryDialog(category: String, refreshTrigger: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val history = remember(refreshTrigger, category) { 
        prefs.getTransactions().filter { it.category == category }.sortedByDescending { it.timestamp } 
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("History: $category") },
        text = {
            if (history.isEmpty()) {
                Text("No transactions found for this category.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(history) { transaction ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(transaction.accountName, fontWeight = FontWeight.Bold)
                                Text("₹${String.format("%.2f", transaction.amount)}", color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                dateFormat.format(Date(transaction.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    val permissions = remember {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list
    }

    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    if (!hasPermissions) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "SMS and Notification permissions are needed to track and categorize your UPI transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = { launcher.launch(permissions.toTypedArray()) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    subScreen: String?,
    onNavigateToSub: (String?) -> Unit,
    initialUpiToEdit: String?,
    refreshTrigger: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    
    if (subScreen == null) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
                
                SettingsMenuItem(
                    title = "Category Management",
                    subtitle = "Add, edit, or remove categories and budgets",
                    icon = Icons.Default.List,
                    onClick = { onNavigateToSub("categories") }
                )
                
                SettingsMenuItem(
                    title = "UPI Default Categories",
                    subtitle = "Manage auto-categorization rules for UPI names",
                    icon = Icons.Default.Star,
                    onClick = { onNavigateToSub("upi_defaults") }
                )
                
                SettingsMenuItem(
                    title = "General Settings",
                    subtitle = "App preferences and budget toggle",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigateToSub("general") }
                )
            }
        }
    } else {
        when (subScreen) {
            "categories" -> CategoryManagementPage(prefs, onRefresh)
            "upi_defaults" -> DefaultCategoryManagementPage(prefs, initialUpiToEdit)
            "general" -> GeneralSettingsPage(prefs, onRefresh)
        }
    }
}

@Composable
fun SettingsMenuItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun CategoryManagementPage(prefs: PreferencesManager, onRefresh: () -> Unit) {
    var allCategories by remember { mutableStateOf(prefs.getAllCategories()) }
    var budgets by remember { mutableStateOf(prefs.getBudgets()) }
    var editingCategoryForBudget by remember { mutableStateOf<String?>(null) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    val budgetEnabled = prefs.isBudgetEnabled()

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text("Categories List", style = MaterialTheme.typography.titleMedium)
            CategoryList(
                categories = allCategories,
                onAdd = { 
                    allCategories = (allCategories + it).distinct()
                    prefs.setAllCategories(allCategories)
                    onRefresh()
                },
                onDelete = {
                    if (allCategories.size > 1) {
                        categoryToDelete = it
                    } else {
                        // Toast or something to say at least one category must exist
                    }
                }
            )
            
            if (budgetEnabled) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Monthly Budgets", style = MaterialTheme.typography.titleMedium)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        allCategories.forEach { category ->
                            val budget = budgets[category] ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { editingCategoryForBudget = category }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(category, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (budget > 0) "₹${String.format("%.2f", budget)}" else "Not set",
                                        color = if (budget > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Budget",
                                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCategoryForBudget?.let { category ->
        SetBudgetDialog(
            category = category,
            currentBudget = budgets[category] ?: 0.0,
            onDismiss = { editingCategoryForBudget = null },
            onSave = { newBudget ->
                if (newBudget > 0) {
                    prefs.setBudget(category, newBudget)
                } else {
                    prefs.removeBudget(category)
                }
                budgets = prefs.getBudgets()
                editingCategoryForBudget = null
                onRefresh()
            }
        )
    }

    categoryToDelete?.let { oldCategory ->
        val remainingCategories = allCategories.filter { it != oldCategory }
        TransferCategoryDialog(
            oldCategory = oldCategory,
            remainingCategories = remainingCategories,
            onDismiss = { categoryToDelete = null },
            onConfirm = { newCategory ->
                prefs.transferAndDeleteCategory(oldCategory, newCategory)
                allCategories = prefs.getAllCategories()
                budgets = prefs.getBudgets()
                categoryToDelete = null
                onRefresh()
            }
        )
    }
}

@Composable
fun TransferCategoryDialog(
    oldCategory: String,
    remainingCategories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(remainingCategories.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category: $oldCategory") },
        text = {
            Column {
                Text("Select a category to transfer all existing transactions and settings from '$oldCategory' to:")
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    remainingCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = category }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category }
                            )
                            Text(category, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Transfer & Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DefaultCategoryManagementPage(prefs: PreferencesManager, initialUpiToEdit: String?) {
    var rememberedCategories by remember { mutableStateOf(prefs.getRememberedCategories()) }
    var editingUpiForCategory by remember { mutableStateOf<String?>(initialUpiToEdit) }
    val allCategories = prefs.getAllCategories()

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text("These are remembered categories for specific UPI IDs.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (rememberedCategories.isEmpty()) {
                Text("No remembered categories yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        rememberedCategories.forEach { (upiName, category) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { editingUpiForCategory = upiName }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(upiName, fontWeight = FontWeight.Bold)
                                    Text("Category: $category", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    IconButton(onClick = { editingUpiForCategory = upiName }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = {
                                        prefs.removeRememberedCategory(upiName)
                                        rememberedCategories = prefs.getRememberedCategories()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingUpiForCategory?.let { upiName ->
        ChangeDefaultCategoryDialog(
            upiName = upiName,
            currentCategory = rememberedCategories[upiName] ?: "",
            allCategories = allCategories,
            onDismiss = { editingUpiForCategory = null },
            onSave = { newCategory, remember ->
                if (remember) {
                    prefs.rememberCategory(upiName, newCategory)
                } else {
                    prefs.removeRememberedCategory(upiName)
                }
                rememberedCategories = prefs.getRememberedCategories()
                editingUpiForCategory = null
            }
        )
    }
}

@Composable
fun GeneralSettingsPage(prefs: PreferencesManager, onRefresh: () -> Unit) {
    var budgetEnabled by remember { mutableStateOf(prefs.isBudgetEnabled()) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Monthly Budgeting", style = MaterialTheme.typography.titleMedium)
                    Text("Show progress bars and limits on the summary screen.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = budgetEnabled,
                    onCheckedChange = { 
                        budgetEnabled = it
                        prefs.setBudgetEnabled(it)
                        onRefresh()
                    }
                )
            }
        }
    }
}

@Composable
fun SetBudgetDialog(
    category: String,
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var budgetText by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget for $category") },
        text = {
            Column {
                Text("Enter the maximum you want to spend in this category per month.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Monthly Budget (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentBudget > 0) {
                    TextButton(
                        onClick = { onSave(0.0) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Remove Budget", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = budgetText.toDoubleOrNull() ?: 0.0
                onSave(amount)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangeDefaultCategoryDialog(
    upiName: String,
    currentCategory: String,
    allCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentCategory) }
    var rememberChoice by remember { mutableStateOf(currentCategory.isNotEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category for $upiName") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text("Remember for future transactions", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                    allCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = category }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category }
                            )
                            Text(category, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedCategory, rememberChoice) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryList(
    categories: List<String>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            TextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("Add Category") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = {
                if (newCategoryName.isNotBlank()) {
                    onAdd(newCategoryName.trim())
                    newCategoryName = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, modifier = Modifier.padding(start = 8.dp))
                        IconButton(onClick = { onDelete(category) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCategorizationDialog(
    accountName: String,
    details: String,
    notificationUuid: String? = null,
    notificationId: Int? = null,
    initialCategory: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val allCategories = remember { prefs.getAllCategories() }
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize Transaction") },
        text = {
            Column {
                Text("To/From: $accountName", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(details, style = MaterialTheme.typography.bodySmall)
                if (initialCategory != null) {
                    Text("Current Category: $initialCategory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text("Remember for future transactions from this UPI", style = MaterialTheme.typography.bodySmall)
                }
                
                Text("Select Category:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(modifier = Modifier
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    allCategories.chunked(2).forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = category == initialCategory
                                Button(
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                    colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    onClick = {
                                        val amount = SmsUtils.extractAmount(details)
                                        val transaction = Transaction(
                                            id = UUID.randomUUID().toString(),
                                            accountName = accountName,
                                            amount = amount,
                                            category = category,
                                            details = details,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        prefs.saveTransaction(transaction)
                                        if (rememberChoice) {
                                            prefs.rememberCategory(accountName, category)
                                        }
                                        
                                        if (notificationUuid != null) {
                                            prefs.updateNotificationStatus(notificationUuid, NotificationStatus.CATEGORIZED, category)
                                        }

                                        if (notificationId != null && notificationId != -1) {
                                            NotificationManagerCompat.from(context).cancel(notificationId)
                                        }

                                        Toast.makeText(context, "Categorized as $category", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                ) {
                                    Text(category, fontSize = 12.sp, maxLines = 1, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (rowCategories.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
