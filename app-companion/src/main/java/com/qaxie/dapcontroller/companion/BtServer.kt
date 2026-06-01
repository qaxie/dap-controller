package com.qaxie.dapcontroller.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.qaxie.dapcontroller.core.DAP_BT_UUID
import com.qaxie.dapcontroller.core.MessageSerializer
import com.qaxie.dapcontroller.core.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class BtServer(private val context: Context) {

    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var clientSocket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun open() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: throw IOException("Bluetooth adapter not available")
        serverSocket = adapter.listenUsingRfcommWithServiceRecord("DapController", DAP_BT_UUID)
    }

    // Suspends until a client connects. Throws IOException if the server socket is closed.
    suspend fun accept(): BluetoothSocket = withContext(Dispatchers.IO) {
        serverSocket?.accept() ?: throw IOException("Server socket is null")
    }

    fun setClientSocket(socket: BluetoothSocket) {
        clientSocket?.safeClose()
        clientSocket = socket
    }

    @Synchronized
    fun send(update: Update) {
        val socket = clientSocket ?: return
        try {
            socket.outputStream.write(MessageSerializer.serialize(update).toByteArray())
            socket.outputStream.flush()
        } catch (e: IOException) {
            socket.safeClose()
            clientSocket = null
        }
    }

    fun closeClientSocket() {
        clientSocket?.safeClose()
        clientSocket = null
    }

    fun close() {
        clientSocket?.safeClose()
        serverSocket?.safeClose()
        clientSocket = null
        serverSocket = null
    }

    private fun BluetoothSocket.safeClose() = try { close() } catch (_: IOException) {}
    private fun BluetoothServerSocket.safeClose() = try { close() } catch (_: IOException) {}
}
