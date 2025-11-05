package com.example.individualcounter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BatchEntry(val timestamp: String, val data: List<Int>)

class IndividualCounterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                IndividualCounterScreen()
            }
        }
    }
}

@Composable
fun IndividualCounterScreen() {
    val context = LocalContext.current

    // Default 5 columns
    var headers by remember { mutableStateOf(listOf("Woman", "Man", "Child", "Elderly", "Disabled")) }

    val batches = remember { mutableStateListOf<BatchEntry>() }

    var showExportDialog by remember { mutableStateOf(false) }
    var showHeaderDialog by remember { mutableStateOf(false) }

    // Simulate incoming batches
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val simulatedData = List(headers.size) { (0..1).random() }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            batches.add(BatchEntry(timestamp, simulatedData))
        }
    }

    val totals = headers.mapIndexed { index, _ -> batches.sumOf { it.data[index] } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Simulated Bluetooth Counter",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        // Scrollable Table
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .background(Color.Gray)
                        .padding(8.dp)
                ) {
                    headers.forEach { header ->
                        Text(
                            header,
                            modifier = Modifier.width(100.dp).padding(4.dp),
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                    }
                    Text(
                        "Timestamp",
                        modifier = Modifier.width(150.dp).padding(4.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }

                // Data rows
                batches.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        entry.data.forEach { value ->
                            Text(
                                value.toString(),
                                modifier = Modifier.width(100.dp).padding(4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            entry.timestamp,
                            modifier = Modifier.width(150.dp).padding(4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Totals:", style = MaterialTheme.typography.titleMedium)
        headers.forEachIndexed { i, h ->
            Text("$h: ${totals[i]}")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // Top button row (edit + undo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { showHeaderDialog = true }) {
                Text("Edit Headers")
            }

            Button(
                onClick = { if (batches.isNotEmpty()) batches.removeAt(batches.lastIndex) },
                enabled = batches.isNotEmpty()
            ) {
                Text("Undo Last Batch")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Second line for Export CSV
        Button(
            onClick = { showExportDialog = true },
            enabled = batches.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export CSV")
        }

        // Edit Header Dialog
        if (showHeaderDialog) {
            EditHeadersDialog(
                headers = headers,
                onDismiss = { showHeaderDialog = false },
                onSave = { newHeaders ->
                    headers = newHeaders
                    showHeaderDialog = false
                }
            )
        }

        // Export Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export CSV") },
                text = { Text("Choose how you want to export the CSV:") },
                confirmButton = {
                    TextButton(onClick = {
                        val csvData = batchesToCsv(headers, batches)
                        exportCsvToDevice(
                            context,
                            "batches_${System.currentTimeMillis()}.csv",
                            csvData
                        )
                        showExportDialog = false
                    }) { Text("Save to Device") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        exportCsvViaShare(context, headers, batches)
                        showExportDialog = false
                    }) { Text("Share CSV") }
                }
            )
        }
    }
}

@Composable
fun EditHeadersDialog(
    headers: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var tempHeaders by remember { mutableStateOf(headers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Column Headers") },
        confirmButton = {
            Button(onClick = { onSave(tempHeaders) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Column {
                tempHeaders.forEachIndexed { index, header ->
                    OutlinedTextField(
                        value = header,
                        onValueChange = { newValue ->
                            tempHeaders = tempHeaders.toMutableList().apply { this[index] = newValue }
                        },
                        label = { Text("Header ${index + 1}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    )
}

// Convert batches to CSV
fun batchesToCsv(headers: List<String>, batches: List<BatchEntry>): String {
    val headerRow = headers.joinToString(",") + ",Timestamp"
    val rows = batches.joinToString("\n") { entry ->
        entry.data.joinToString(",") + ",${entry.timestamp}"
    }
    return "$headerRow\n$rows"
}

// Save CSV to device
fun exportCsvToDevice(context: Context, fileName: String, data: String): String {
    val fileDir = context.getExternalFilesDir(null)
    val file = File(fileDir, fileName)

    return try {
        file.writeText(data)
        Toast.makeText(context, "CSV saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
        ""
    }
}

// Share CSV via FileProvider
fun exportCsvViaShare(context: Context, headers: List<String>, batches: List<BatchEntry>) {
    val csv = batchesToCsv(headers, batches)
    val file = File(context.cacheDir, "batches.csv")
    file.writeText(csv)
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Batches CSV"))
}
