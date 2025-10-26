package com.example.simulatedbuttoncounter

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MultiButtonCounterWithDelete()
            }
        }
    }
}

@Composable
fun MultiButtonCounterWithDelete() {
    // Keep all events for counters
    val allEvents = remember { mutableStateListOf<String>() }

    // Current batch log (cleared on each new batch)
    val currentBatch = remember { mutableStateListOf<String>() }

    // Timestamp for the current batch
    var timestamp by remember { mutableStateOf(currentTime()) }

    // Derived counts
    val countA by remember { derivedStateOf { allEvents.count { it == "A" } } }
    val countB by remember { derivedStateOf { allEvents.count { it == "B" } } }
    val countC by remember { derivedStateOf { allEvents.count { it == "C" } } }
    val countDel by remember { derivedStateOf {
        allEvents.count { it == "DEL" } + currentBatch.count { it == "DEL" }
    } }

    // Simulate incoming batches indefinitely
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val batch = generateRandomBatch().toMutableList()

            // Process DEL events in the batch
            val processedBatch = mutableListOf<String>()
            for (event in batch) {
                if (event == "DEL") {
                    // Remove last non-DEL from batch if exists
                    val lastIndex = processedBatch.indexOfLast { it != "DEL" }
                    if (lastIndex != -1) processedBatch.removeAt(lastIndex)
                    processedBatch.add("DEL") // keep DEL in batch for log
                } else {
                    processedBatch.add(event)
                }
            }

            // Update current batch and timestamp
            currentBatch.clear()
            currentBatch.addAll(processedBatch)
            timestamp = currentTime()

            // Update allEvents for counters (ignore DELs for A/B/C)
            allEvents.addAll(processedBatch.filter { it != "DEL" })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Multi-Button Counter (Simulated Bluetooth)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        CounterDisplay("Button A", countA) {
            currentBatch.add("A")
            allEvents.add("A")
            timestamp = currentTime()
        }
        CounterDisplay("Button B", countB) {
            currentBatch.add("B")
            allEvents.add("B")
            timestamp = currentTime()
        }
        CounterDisplay("Button C", countC) {
            currentBatch.add("C")
            allEvents.add("C")
            timestamp = currentTime()
        }
        CounterDisplay("Delete (Undo)", countDel) {
            if (currentBatch.isNotEmpty()) {
                val last = currentBatch.removeAt(currentBatch.lastIndex)
                if (last != "DEL" && allEvents.isNotEmpty()) {
                    allEvents.removeAt(allEvents.lastIndex)
                }
                currentBatch.add("DEL") // count this manual delete
                timestamp = currentTime()
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Current Batch:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (currentBatch.isNotEmpty()) {
            Text("[${timestamp}] ${currentBatch.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("No events", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Generate a random batch of events, may include DEL
fun generateRandomBatch(): List<String> {
    val options = listOf("A", "B", "C")
    val count = Random.nextInt(2, 5)
    val batch = MutableList(count) { options.random() }
    if (Random.nextFloat() < 0.3f) batch.add("DEL") // 30% chance
    return batch
}

// Helper to get current timestamp
fun currentTime(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

@Composable
fun CounterDisplay(label: String, count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: $count", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClick) { Text("Simulate Press") }
        }
    }
}
