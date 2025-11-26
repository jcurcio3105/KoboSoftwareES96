package com.example.individualcounter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Popup dialog to scan BLE devices and connect
 */
@Composable
fun ScanDialog(
    context: Context,
    bleManager: BLEManager,
    onDismiss: () -> Unit
) {
    val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var scanning by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    if (!devices.contains(device)) {
                        devices = devices + device
                    }
                    Log.d("BLE_SCAN", "Found device: ${device.name ?: "Unnamed"} (${device.address})")
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { r ->
                    r.device?.let { device ->
                        if (!devices.contains(device)) {
                            devices = devices + device
                        }
                        Log.d("BLE_SCAN", "Found device in batch: ${device.name ?: "Unnamed"} (${device.address})")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Toast.makeText(context, "BLE scan failed: $errorCode", Toast.LENGTH_SHORT).show()
                Log.e("BLE_SCAN", "Scan failed with error: $errorCode")
            }
        }
    }

    // Start scan when dialog appears
    LaunchedEffect(Unit) {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        scanning = true

        handler.postDelayed({
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
            Log.d("BLE_SCAN", "Scan stopped after 10 seconds")
        }, 10000) // scan for 10 seconds
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Select BLE Device") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (scanning) {
                    Text("Scanning...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    bleManager.connectToDevice(device)
                                    Toast.makeText(
                                        context,
                                        "Connecting to ${device.name ?: device.address}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(device.name ?: "Unnamed", modifier = Modifier.weight(1f))
                            Text(device.address)
                        }
                    }
                }
            }
        }
    )
}


