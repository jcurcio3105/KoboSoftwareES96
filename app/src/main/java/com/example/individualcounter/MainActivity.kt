package com.example.individualcounter


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


data class BatchEntry(val timestamp: String, val data: MutableList<Int>)


class IndividualCounterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                DynamicColumnCounterScreen()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicColumnCounterScreen() {
    val context = LocalContext.current


    var headers by remember { mutableStateOf(listOf("Women", "Men", "Elderly")) }
    val batches = remember { mutableStateListOf<BatchEntry>() }


    // Simulate data every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val simulatedData = List(headers.size) { if (Random.nextBoolean()) 1 else 0 }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            batches.add(BatchEntry(timestamp, simulatedData.toMutableList()))
        }
    }


    val totals = headers.indices.map { col -> batches.sumOf { it.data[col] } }
    var expanded by remember { mutableStateOf(false) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Dynamic Entry Logger", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))


        val horizontalScroll = rememberScrollState()
        Box(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {


                // Editable Header Row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Time", modifier = Modifier.width(100.dp))
                    headers.forEachIndexed { index, header ->
                        var editingText by remember(header) { mutableStateOf(header) }


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


                // Data Rows
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
                            Text(entry.timestamp, modifier = Modifier.width(100.dp))
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


        // Totals Row
        Text("Totals:", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            headers.forEachIndexed { index, header -> Text("$header: ${totals[index]}") }
        }


        Spacer(Modifier.height(24.dp))


        // Column Controls
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                if (headers.size < 5) {
                    headers = headers + "New Column"
                    batches.forEach { it.data.add(0) }
                } else {
                    Toast.makeText(context, "Maximum of 5 columns reached", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Add Column") }


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


        // Undo + Save / Share Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { if (batches.isNotEmpty()) batches.removeAt(batches.lastIndex) },
                enabled = batches.isNotEmpty()
            ) { Text("Undo Last Batch") }
        }


        Spacer(Modifier.height(16.dp))


        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val csv = batchesToCsv(batches, headers)
                    exportCsvToDevice(context, "batches_${System.currentTimeMillis()}.csv", csv)
                },
                enabled = batches.isNotEmpty()
            ) { Text("Save CSV") }


            Button(
                onClick = { exportCsvViaShare(context, batches, headers) },
                enabled = batches.isNotEmpty()
            ) { Text("Share CSV") }
        }
    }
}


// --- CSV and File Export Functions ---


fun batchesToCsv(batches: List<BatchEntry>, headers: List<String>): String {
    val headerRow = "Timestamp," + headers.joinToString(",")
    val rows = batches.joinToString("\n") { "${it.timestamp},${it.data.joinToString(",")}" }
    return "$headerRow\n$rows"
}


fun exportCsvToDevice(context: Context, fileName: String, data: String) {
    try {
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(data)
        Toast.makeText(context, "CSV saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
    }
}


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
