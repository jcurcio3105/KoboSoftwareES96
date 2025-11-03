package com.example.individualcounter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                SimulatedBluetoothCounterScreen()
            }
        }
    }
}

@Composable
fun SimulatedBluetoothCounterScreen() {
    val context = LocalContext.current
    val batches = remember { mutableStateListOf<BatchEntry>() }

    // Dialog state for export options
    var showExportDialog by remember { mutableStateOf(false) }

    // Simulate automatic batch arrival every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val simulatedData = listOf(
                listOf(1, 0, 1),
                listOf(0, 1, 0),
                listOf(1, 0, 0),
                listOf(0, 1, 1)
            ).random()
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            batches.add(BatchEntry(timestamp, simulatedData))
        }
    }

    val totalFemale = batches.sumOf { it.data[0] }
    val totalMale = batches.sumOf { it.data[1] }
    val totalElderly = batches.sumOf { it.data[2] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Simulated Bluetooth Characteristic Logger",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        BatchTable(batches)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Totals:", style = MaterialTheme.typography.titleMedium)
        Text("Female: $totalFemale | Male: $totalMale | Elderly: $totalElderly")

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // Buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { if (batches.isNotEmpty()) batches.removeAt(batches.lastIndex) },
                enabled = batches.isNotEmpty()
            ) {
                Text("Undo Last Batch")
            }

            Button(
                onClick = { showExportDialog = true },
                enabled = batches.isNotEmpty()
            ) {
                Text("Export CSV")
            }
        }

        // Export choice dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export CSV") },
                text = { Text("Choose how you want to export the CSV:") },
                confirmButton = {
                    TextButton(onClick = {
                        val csvData = batchesToCsv(batches)
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
                        exportCsvViaShare(context, batches)
                        showExportDialog = false
                    }) { Text("Share CSV") }
                }
            )
        }
    }
}

@Composable
fun BatchTable(batches: List<BatchEntry>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Time", style = MaterialTheme.typography.titleSmall)
            Text("Female", style = MaterialTheme.typography.titleSmall)
            Text("Male", style = MaterialTheme.typography.titleSmall)
            Text("Elderly", style = MaterialTheme.typography.titleSmall)
        }
        HorizontalDivider()

        // Each batch displayed as a row
        batches.forEach { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entry.timestamp)
                    Text(entry.data[0].toString())
                    Text(entry.data[1].toString())
                    Text(entry.data[2].toString())
                }
            }
        }
    }
}

// Convert batches to CSV
fun batchesToCsv(batches: List<BatchEntry>): String {
    val header = "Timestamp,Female,Male,Elderly"
    val rows = batches.joinToString("\n") { entry ->
        "${entry.timestamp},${entry.data[0]},${entry.data[1]},${entry.data[2]}"
    }
    return "$header\n$rows"
}

// Save CSV to device (app's external files directory)
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
fun exportCsvViaShare(context: Context, batches: List<BatchEntry>) {
    val csv = batchesToCsv(batches)
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
