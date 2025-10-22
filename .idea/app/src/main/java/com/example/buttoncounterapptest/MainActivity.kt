package com.example.buttoncounterapptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.buttoncounterapptest.ui.theme.ButtonCounterAppTestTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ButtonCounterAppTestTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MultiButtonCounterWithDelete()
                }
            }
        }
    }
}

@Composable
fun MultiButtonCounterWithDelete() {
    val events = remember { mutableStateListOf<String>() }

    // Derived counts
    val countA = events.count { it == "A" }
    val countB = events.count { it == "B" }
    val countC = events.count { it == "C" }

    // Simulate incoming Bluetooth batches indefinitely
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000) // Every 3 seconds new batch
            val batch = generateRandomBatch()
            processBatch(batch, events)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Multi-Button Counter (Simulated Bluetooth)",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))

        CounterDisplay("Button A", countA) { events.add("A") }
        CounterDisplay("Button B", countB) { events.add("B") }
        CounterDisplay("Button C", countC) { events.add("C") }
        CounterDisplay("Delete (Undo)", 0) {
            if (events.isNotEmpty()) events.removeAt(events.lastIndex)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Spacer(Modifier.height(12.dp))

        Text("Event Log:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = events.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}

// Generate random batch strings like "A,B,DEL,A"
fun generateRandomBatch(): String {
    val options = listOf("A", "B", "C")
    val count = Random.nextInt(2, 5)
    val includeDelete = Random.nextFloat() < 0.3f // 30% chance to include delete
    val batchItems = MutableList(count) { options.random() }
    if (includeDelete) batchItems.add("DEL")
    return batchItems.joinToString(",")
}

// Process each item in the batch
fun processBatch(batch: String, events: MutableList<String>) {
    val items = batch.split(",")
    for (item in items) {
        when (item.trim()) {
            "A", "B", "C" -> events.add(item)
            "DEL" -> if (events.isNotEmpty()) events.removeAt(events.lastIndex)
        }
    }
}

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
            Button(onClick = onClick) {
                Text("Simulate Press")
            }
        }
    }
}
