package com.example.pips

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pips.ui.theme.PIPSTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private val transactionDetailsState = mutableStateOf<String?>(null)
    private val accountNameState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            PIPSTheme {
                val transactionDetails by transactionDetailsState
                val accountName by accountNameState
                var currentScreen by remember { mutableStateOf("summary") }
                var selectedCategoryForHistory by remember { mutableStateOf<String?>(null) }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // Using a custom Surface instead of Experimental CenterAlignedTopAppBar
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
                                    IconButton(onClick = { currentScreen = "summary" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(48.dp))
                                }

                                Text(
                                    text = "PIPS - UPI Tracker",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )

                                if (currentScreen == "summary") {
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
                                    onCategoryClick = { selectedCategoryForHistory = it }
                                )
                                "settings" -> SettingsScreen()
                            }
                        }

                        if (transactionDetails != null) {
                            TransactionCategorizationDialog(
                                accountName = accountName ?: "Unknown",
                                details = transactionDetails!!,
                                onDismiss = { 
                                    transactionDetailsState.value = null
                                    accountNameState.value = null
                                }
                            )
                        }

                        selectedCategoryForHistory?.let { category ->
                            CategoryHistoryDialog(
                                category = category,
                                onDismiss = { selectedCategoryForHistory = null }
                            )
                        }
                    }
                }
            }
        }
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
            }
        }
    }
}

@Composable
fun CategorySummaryScreen(onCategoryClick: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val transactions = remember { mutableStateListOf<Transaction>().apply { addAll(prefs.getTransactions()) } }
    val categories = remember { prefs.getAllCategories() }

    val categoryTotals = categories.associateWith { category ->
        transactions.filter { it.category == category }.sumOf { it.amount }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "Spending Summary",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(categories) { category ->
            val total = categoryTotals[category] ?: 0.0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onCategoryClick(category) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "₹${String.format("%.2f", total)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryHistoryDialog(category: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val history = remember { prefs.getTransactions().filter { it.category == category }.sortedByDescending { it.timestamp } }
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
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    
    var notificationCategories by remember { mutableStateOf(prefs.getNotificationCategories()) }
    var extraCategories by remember { mutableStateOf(prefs.getExtraCategories()) }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text("Notification Quick Actions", style = MaterialTheme.typography.titleLarge)
            Text("Max 3 buttons will be visible in the notification.", style = MaterialTheme.typography.bodySmall)
            CategoryList(
                categories = notificationCategories,
                onAdd = { 
                    notificationCategories = (notificationCategories + it).distinct()
                    prefs.setNotificationCategories(notificationCategories)
                },
                onDelete = {
                    notificationCategories = notificationCategories.filter { cat -> cat != it }
                    prefs.setNotificationCategories(notificationCategories)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Extra App Categories", style = MaterialTheme.typography.titleLarge)
            Text("These appear only inside the app dialog.", style = MaterialTheme.typography.bodySmall)
            CategoryList(
                categories = extraCategories,
                onAdd = { 
                    extraCategories = (extraCategories + it).distinct()
                    prefs.setExtraCategories(extraCategories)
                },
                onDelete = {
                    extraCategories = extraCategories.filter { cat -> cat != it }
                    prefs.setExtraCategories(extraCategories)
                }
            )
        }
    }
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val allCategories = remember { prefs.getAllCategories() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize Transaction") },
        text = {
            Column {
                Text("To/From: $accountName", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(details, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Category:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Using non-experimental Row/Column with scroll for categorization
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
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
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
                                        Toast.makeText(context, "Categorized as $category", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                ) {
                                    Text(category, fontSize = 12.sp, maxLines = 1)
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
