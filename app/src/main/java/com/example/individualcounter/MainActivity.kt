package com.example.individualcounter

// --- Android imports ---
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// --- Data model representing a single batch (row) of entries ---
data class BatchEntry(val timestamp: String, val data: MutableList<Int>)

// --- Main Activity for the app ---
class IndividualCounterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Makes UI draw behind system bars (modern look)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                DynamicColumnCounterScreen() // Calls our main screen composable
            }
        }
    }
}

// --- Main UI composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicColumnCounterScreen() {
    val context = LocalContext.current

    // Default headers for each category/column
    var headers by remember { mutableStateOf(listOf("Women", "Men", "Elderly")) }

    // Holds all batch entries logged
    val batches = remember { mutableStateListOf<BatchEntry>() }

    // --- Simulated data generation loop ---
    // Every 5 seconds, generate a random batch (like receiving CSV data)
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            // Randomly generate 1s or 0s for each header column
            val simulatedData = List(headers.size) { if (Random.nextBoolean()) 1 else 0 }

            // Timestamp when this batch is created
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            // Add new entry to the batches list
            batches.add(BatchEntry(timestamp, simulatedData.toMutableList()))
        }
    }

    // Calculate total counts per column
    val totals = headers.indices.map { col -> batches.sumOf { it.data[col] } }

    // Used to toggle dropdown for "Remove Column"
    var expanded by remember { mutableStateOf(false) }

    // --- Layout starts here ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Dynamic Entry Logger", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Enables horizontal scrolling when many columns exist
        val horizontalScroll = rememberScrollState()
        Box(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // --- Editable Header Row ---
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Time", modifier = Modifier.width(100.dp))
                    headers.forEachIndexed { index, header ->
                        var editingText by remember(header) { mutableStateOf(header) }

                        // Editable header name input
                        BasicTextField(
                            value = editingText,
                            onValueChange = {
                                editingText = it
                                headers = headers.toMutableList().apply { set(index, it) }
                            },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .width(100.dp)
                                .padding(4.dp)
                        )
                    }
                }

                HorizontalDivider()

                // --- Data Rows: display each batch ---
                batches.forEach { entry ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            // Timestamp column
                            Text(entry.timestamp, modifier = Modifier.width(100.dp))

                            // Display each column value (1 or 0)
                            entry.data.forEach { value ->
                                Text(value.toString(), modifier = Modifier.width(100.dp))
                            }
                        }
                    }
                }

                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Totals row at the bottom ---
        Text("Totals:", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            headers.forEachIndexed { index, header -> Text("$header: ${totals[index]}") }
        }

        Spacer(Modifier.height(24.dp))

        // --- Add/Remove Column controls ---
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Add a new column (up to 5)
            Button(onClick = {
                if (headers.size < 5) {
                    headers = headers + "New Column"
                    batches.forEach { it.data.add(0) }
                } else {
                    Toast.makeText(context, "Maximum of 5 columns reached", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Add Column") }

            // Dropdown for removing columns
            Box {
                Button(onClick = { expanded = true }) { Text("Remove Column") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    headers.forEach { header ->
                        DropdownMenuItem(
                            text = { Text(header) },
                            onClick = {
                                expanded = false
                                val index = headers.indexOf(header)
                                if (index != -1) {
                                    headers = headers.filterIndexed { i, _ -> i != index }
                                    batches.forEach { it.data.removeAt(index) }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Undo Button: removes last batch ---
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { if (batches.isNotEmpty()) batches.removeAt(batches.lastIndex) },
                enabled = batches.isNotEmpty()
            ) { Text("Undo Last Batch") }
        }

        Spacer(Modifier.height(16.dp))

        // --- Save + Share CSV Buttons ---
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Save to device storage
            Button(
                onClick = {
                    val csv = batchesToCsv(batches, headers)
                    exportCsvToDevice(context, "batches_${System.currentTimeMillis()}.csv", csv)
                },
                enabled = batches.isNotEmpty()
            ) { Text("Save CSV") }

            // Share CSV via external apps (Gmail, Drive, etc.)
            Button(
                onClick = { exportCsvViaShare(context, batches, headers) },
                enabled = batches.isNotEmpty()
            ) { Text("Share CSV") }
        }
    }
}

// --- Helper functions for CSV export ---

// Converts all batches into CSV string format
fun batchesToCsv(batches: List<BatchEntry>, headers: List<String>): String {
    val headerRow = "Timestamp," + headers.joinToString(",")
    val rows = batches.joinToString("\n") { "${it.timestamp},${it.data.joinToString(",")}" }
    return "$headerRow\n$rows"
}

// Saves CSV file to external storage
fun exportCsvToDevice(context: Context, fileName: String, data: String) {
    try {
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(data)
        Toast.makeText(context, "CSV saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// Shares CSV file via Android's share intent
fun exportCsvViaShare(context: Context, batches: List<BatchEntry>, headers: List<String>) {
    val csv = batchesToCsv(batches, headers)
    val file = File(context.cacheDir, "batches.csv")
    file.writeText(csv)

    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Batches CSV"))
}
