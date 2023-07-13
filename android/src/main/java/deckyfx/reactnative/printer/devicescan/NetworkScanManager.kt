package deckyfx.reactnative.printer.devicescan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.Socket

class NetworkScanManager {
  var onNetworkScanListener: OnNetworkScanListener? = null
  private var mIsRunning = false

  interface OnNetworkScanListener {
    fun deviceFound(ip: String, port: Int, deviceName: String)
    fun startScan()
    fun stopScan()
    fun error(error: Exception)
  }

  /**
   * Start Scanning for discoverable devices
   */
  suspend fun startScan() {
    if (mIsRunning) return
    mIsRunning = true
    onNetworkScanListener?.startScan()
    withContext(Dispatchers.IO) { Dispatchers.IO
      val job = async { getIPV4Address() }
      val ip = job.await()
      ip?.let{ _ip ->
        val ipSegments = _ip.split(".").toMutableList()
        coroutineScope {
          (1..254).map { i ->
            async {
              ipSegments[3] = i.toString()
              val host = ipSegments.joinToString(".")
              val port = PRINTER_PORT
              val deviceName = socketConnect(host, port)
              if (!deviceName.isNullOrEmpty()) {
                onNetworkScanListener?.deviceFound(host, port, deviceName)
              }
            }
          }.awaitAll()
          stopScan()
        }
      }
    }
  }

  /**
   * To Stop Scanning process
   */
  fun stopScan() {
    mIsRunning = false
    onNetworkScanListener?.stopScan()
  }

  @Suppress("SameParameterValue")
  private fun socketConnect(host: String?, port: Int? = PRINTER_PORT): String? {
    if (!mIsRunning) return null
    val socketAddress: InetSocketAddress = if (host != null) {
      InetSocketAddress(InetAddress.getByName(host), port!!)
    } else {
      InetSocketAddress(port!!)
    }
    return try {
      val socket = Socket()
      socket.connect(socketAddress, SOCKET_TIMEOUT)
      PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())),true)

      val message = byteArrayOf(0x1d, 0x49, 0x42, 0x1d, 0x49, 0x43)
      val socketOutputStream = socket.getOutputStream()
      socketOutputStream.write(message)

      val input = BufferedReader(InputStreamReader(socket.getInputStream()))
      val buffer = StringBuilder()
      while (input.ready()) {
        buffer.append(input.readLine())
      }
      socket.close()
      buffer.toString()
    } catch (e: IOException) {
      onNetworkScanListener?.error(e)
      null
    }
  }

  private val inetAddresses: List<InterfaceAddress>
    get() {
      val addresses: MutableList<InterfaceAddress> = ArrayList()
      try {
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
          @SuppressWarnings("SpellCheckingInspection")
          val networkInterface = en.nextElement()
          for (interface_address in networkInterface.interfaceAddresses) {
            addresses.add(interface_address)
          }
        }
      } catch (e: Exception) {
        onNetworkScanListener?.error(e)
      }
      return addresses
    }

  @Suppress("ImplicitThis")
  private fun inDSLITERange(ip: String): Boolean {
    // Fixes issue https://github.com/pusherman/react-native-network-info/issues/43
    // Based on comment
    // https://github.com/pusherman/react-native-network-info/issues/43#issuecomment-358360692
    // added this check in getIPAddress and getIPV4Address
    return DSLITE_LIST.contains(ip)
  }

  private fun getIPV4Address(): String? {
    return try {
      var ipAddress: String? = null
      var tmp = "0.0.0.0"
      for (address in inetAddresses) {
        if (!address.address.isLoopbackAddress && address.address is Inet4Address) {
          address.address.hostAddress?.let { _address ->
            tmp = _address
          }
          if (!inDSLITERange(tmp)) {
            ipAddress = tmp
          }
        }
      }
      ipAddress.toString()
    } catch (e: Exception) {
      onNetworkScanListener?.error(e)
      null
    }
  }

  companion object {
    const val PRINTER_PORT = 9100
    const val SOCKET_TIMEOUT = 50

    private val LOG_TAG = NetworkScanManager::class.java.simpleName
    private val DSLITE_LIST = listOf(
      "192.0.0.0",
      "192.0.0.1",
      "192.0.0.2",
      "192.0.0.3",
      "192.0.0.4",
      "192.0.0.5",
      "192.0.0.6",
      "192.0.0.7")
  }
}