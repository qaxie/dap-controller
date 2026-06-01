package com.qaxie.dapcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.qaxie.dapcontroller.core.Command
import com.qaxie.dapcontroller.core.DAP_BT_UUID
import com.qaxie.dapcontroller.core.MessageParser
import com.qaxie.dapcontroller.core.MessageSerializer
import com.qaxie.dapcontroller.core.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

class BtClient {

    @Volatile private var socket: BluetoothSocket? = null

    val updates: Flow<Update> = flow {
        val sock = socket ?: return@flow
        val reader = sock.inputStream.bufferedReader()
        while (true) {
            val line = withContext(Dispatchers.IO) {
                try { reader.readLine() } catch (e: IOException) { null }
            } ?: break
            MessageParser.parseUpdate(line)?.let { emit(it) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        val sock = device.createRfcommSocketToServiceRecord(DAP_BT_UUID)
        try {
            withTimeout(10_000) {
                withContext(Dispatchers.IO) { sock.connect() }
            }
        } catch (e: TimeoutCancellationException) {
            sock.safeClose()
            throw IOException("Connection timed out")
        } catch (e: IOException) {
            sock.safeClose()
            throw e
        }
        socket = sock
    }

    suspend fun send(command: Command) {
        val sock = socket ?: throw IOException("Not connected")
        withContext(Dispatchers.IO) {
            sock.outputStream.write(MessageSerializer.serialize(command).toByteArray())
            sock.outputStream.flush()
        }
    }

    fun close() {
        socket?.safeClose()
        socket = null
    }

    private fun BluetoothSocket.safeClose() = try { close() } catch (_: IOException) {}
}
