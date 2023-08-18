package deckyfx.reactnative.printer

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import deckyfx.reactnative.printer.devicescan.DeviceScanner
import deckyfx.reactnative.printer.devicescan.NetworkScanManager
import deckyfx.reactnative.printer.escposprinter.EscPosPrinter
import deckyfx.reactnative.printer.escposprinter.connection.DeviceConnection
import deckyfx.reactnative.printer.escposprinter.connection.bluetooth.BluetoothPrintersConnectionsManager
import deckyfx.reactnative.printer.escposprinter.connection.serial.SerialConnection
import deckyfx.reactnative.printer.escposprinter.connection.serial.SerialConnectionsManager
import deckyfx.reactnative.printer.escposprinter.connection.tcp.TcpConnection
import deckyfx.reactnative.printer.escposprinter.connection.usb.UsbPrintersConnectionsManager
import deckyfx.reactnative.printer.worker.PrintingWorkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class RNPrinter(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var workerListenerInitialized: Boolean = false
  private var mListenerCount: Int = 0

  override fun getName(): String {
    return LOG_TAG
  }

  companion object {
    private val LOG_TAG = RNPrinter::class.java.simpleName

    const val EVENT_PRINTING_JOB = "PRINTING_JOB"

    const val PRINTER_CONNECTION_NETWORK = "network"
    const val PRINTER_CONNECTION_BLUETOOTH = "bluetooth"
    const val PRINTER_CONNECTION_USB = "usb"
    const val PRINTER_CONNECTION_SERIAL = "serial"

    const val PRINTER_TYPE_THERMAL = "thermal"
    const val PRINTER_TYPE_DOTMATRIX = "dotmatrix"

    const val PRINTING_DPI_NORMAL = 210
    const val PRINTING_LINES_MAX_CHAR_33 = 33
    const val PRINTING_LINES_MAX_CHAR_40 = 40
    const val PRINTING_LINES_MAX_CHAR_42 = 42
    const val PRINTING_LINES_MAX_CHAR_56 = 56
    const val PRINTING_WIDTH_58_MM = 41f
    const val PRINTING_WIDTH_76_MM = 48f
    const val PRINTING_WIDTH_80_MM = 60f

    const val TEST_PRINT_DESIGN =
       // "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, this.getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM))+"</img>\n" +
      "[L]\n" +
      "[C]<u><font size='big'>ORDER N°045</font></u>\n" +
      "[L]\n" +
      "[C]================================\n" +
      "[L]\n" +
      "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99e\n" +
      "[L]  + Size : S\n" +
      "[L]\n" +
      "[L]<b>AWESOME HAT</b>[R]24.99e\n" +
      "[L]  + Size : 57/58\n" +
      "[L]\n" +
      "[C]--------------------------------\n" +
      "[R]TOTAL PRICE :[R]34.98e\n" +
      "[R]TAX :[R]4.23e\n" +
      "[L]\n" +
      "[C]================================\n" +
      "[L]\n" +
      "[L]<font size='tall'>Customer :</font>\n" +
      "[L]Raymond DUPONT\n" +
      "[L]5 rue des girafes\n" +
      "[L]31547 PERPETES\n" +
      "[L]Tel : +33801201456\n" +
      "[L]\n" +
      "[C]<barcode type='ean13' height='10'>831254784551</barcode>\n" +
      "[C]<qrcode size='20'>https://dantsu.com/</qrcode>\n" +
      "[L]\n" +
      "[L]\n" +
      "[L]\n" +
      "[L]\n" +
      "[L]\n"+
      "[L]\n"
  }

  override fun getConstants(): Map<String, Any> {
    val constants: MutableMap<String, Any> = HashMap()
    constants["EVENT_PRINTING_JOB"] = EVENT_PRINTING_JOB

    constants["PRINTER_CONNECTION_NETWORK"] = PRINTER_CONNECTION_NETWORK
    constants["PRINTER_CONNECTION_BLUETOOTH"] = PRINTER_CONNECTION_BLUETOOTH
    constants["PRINTER_CONNECTION_USB"] = PRINTER_CONNECTION_USB
    constants["PRINTER_CONNECTION_SERIAL"] = PRINTER_CONNECTION_SERIAL


    constants["PRINTER_TYPE_THERMAL"] = PRINTER_TYPE_THERMAL
    constants["PRINTER_TYPE_DOTMATRIX"] = PRINTER_TYPE_DOTMATRIX

    constants["PRINTING_DPI_NORMAL"] = PRINTING_DPI_NORMAL

    constants["PRINTING_LINES_MAX_CHAR_33"] = PRINTING_LINES_MAX_CHAR_33
    constants["PRINTING_LINES_MAX_CHAR_40"] = PRINTING_LINES_MAX_CHAR_40
    constants["PRINTING_LINES_MAX_CHAR_42"] = PRINTING_LINES_MAX_CHAR_42
    constants["PRINTING_LINES_MAX_CHAR_56"] = PRINTING_LINES_MAX_CHAR_56

    constants["PRINTING_WIDTH_58_MM"] = PRINTING_WIDTH_58_MM
    constants["PRINTING_WIDTH_76_MM"] = PRINTING_WIDTH_76_MM
    constants["PRINTING_WIDTH_80_MM"] = PRINTING_WIDTH_80_MM

    constants["TEST_PRINT_DESIGN"] = TEST_PRINT_DESIGN
    return constants
  }

  init {
  }

  @ReactMethod
  fun checkPermissions(args: ReadableMap, promise: Promise) {
    val config = ScanTypeArgument(args)
    if (config.connection == DeviceScanner.SCAN_NETWORK || config.connection == DeviceScanner.SCAN_ZEROCONF) {
      if (!checkPermission(Manifest.permission.INTERNET)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)) {
        promise.resolve(false)
        return
      }
      promise.resolve(true)
      return
    }
    if (config.connection == DeviceScanner.SCAN_USB) {
      promise.resolve(false)
      return
    }
    if (config.connection == DeviceScanner.SCAN_SERIAL) {
      promise.resolve(false)
      return
    }
    if (config.connection == DeviceScanner.SCAN_BLUETOOTH) {
      if (!checkPermission(Manifest.permission.BLUETOOTH)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
        promise.resolve(false)
        return
      }
      promise.resolve(true)
      return
    }
    if (config.connection == DeviceScanner.SCAN_ALL) {
      if (!checkPermission(Manifest.permission.INTERNET)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
        promise.resolve(false)
        return
      }
      if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
        promise.resolve(false)
        return
      }
      promise.resolve(true)
      return
    }
  }

  @ReactMethod
  fun requestPermissions(args: ReadableMap, promise: Promise) {
    val config = ScanTypeArgument(args)
    if (config.connection == DeviceScanner.SCAN_NETWORK || config.connection == DeviceScanner.SCAN_ZEROCONF) {
      val PERMISSION_ALL = 1
      val PERMISSIONS = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
      )
      ActivityCompat.requestPermissions(reactContext.currentActivity!!, PERMISSIONS, PERMISSION_ALL)
      promise.resolve(true)
      return
    }
    if (config.connection == DeviceScanner.SCAN_USB) {
      UsbPrintersConnectionsManager(reactContext).requestUSBPermissions(reactContext, usbReceiver)
      promise.resolve(true)
    }
    if (config.connection == DeviceScanner.SCAN_SERIAL) {
      UsbPrintersConnectionsManager(reactContext).requestUSBPermissions(reactContext, usbReceiver)
      promise.resolve(true)
    }
    if (config.connection == DeviceScanner.SCAN_BLUETOOTH) {
      val PERMISSION_ALL = 1
      val PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
      )
      ActivityCompat.requestPermissions(reactContext.currentActivity!!, PERMISSIONS, PERMISSION_ALL)
      promise.resolve(true)
      return
    }
    if (config.connection == DeviceScanner.SCAN_ALL) {
      val PERMISSION_ALL = 1
      val PERMISSIONS = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
      )
      ActivityCompat.requestPermissions(reactContext.currentActivity!!, PERMISSIONS, PERMISSION_ALL)

      val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
      UsbPrintersConnectionsManager(reactContext).list?.forEach {
        if (it != null) {
          val permissionIntent = PendingIntent.getBroadcast(
            reactContext,
            0,
            Intent(DeviceScanner.ACTION_USB_PERMISSION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
          )
          val filter: IntentFilter = IntentFilter(DeviceScanner.ACTION_USB_PERMISSION)
          reactContext.registerReceiver(usbReceiver, filter)
          usbManager.requestPermission(it.device, permissionIntent)
        }
      }

      promise.resolve(true)
      return
    }
    promise.resolve(false)
  }

  @ReactMethod
  fun getUsbPrintersCount(promise: Promise) {
    val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
    UsbPrintersConnectionsManager(reactContext).list?.size?.let {
      promise.resolve(it)
      return
    }
    promise.resolve(0)
  }

  private fun checkPermission(permission: String): Boolean {
    if (ActivityCompat.checkSelfPermission(reactContext, permission) != PackageManager.PERMISSION_GRANTED) {
      return false
    }
    return true
  }

  @ReactMethod
  fun write(config: ReadableMap, text:String, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        printer.printFormattedText(text, 0)
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun cutPaper(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        printer.cutPaper()
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun feedPaper(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        printer.feedPaper(0)
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun openCashBox(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        printer.openCashBox()
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun testConnection(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun getPrinterModel(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        val model = printer.getPrinterModel()
        promise.resolve(model)
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve("")
  }

  @ReactMethod
  fun testPrint(config: ReadableMap, promise: Promise) {
    val printer = resolvePrinter(config)
    printer?.let {
      try {
        printer.printFormattedTextAndCut(TEST_PRINT_DESIGN, 0)
        return
      } catch (e: Exception) {
        promise.reject(e)
        return
      }
    }
    promise.resolve(true)
  }

  @ReactMethod
  fun enqueuePrint(config: ReadableMap, text:String, cutPaper: Boolean = true, openCashBox: Boolean = true, promise:Promise) {
    initWorkerListener()
    val uuid = PrintingWorkerManager.getInstance().enqueuePrint(reactContext, config, text, cutPaper, openCashBox)
    promise.resolve(uuid?.toString())
  }

  @ReactMethod
  fun prunePrintingWorks() {
    WorkManager.getInstance(reactContext).pruneWork()
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun multiply(a: Double, b: Double, promise: Promise) {
    promise.resolve(a * b * 0)
  }

  private fun resolvePrinter(config: ReadableMap): EscPosPrinter? {
    return resolvePrinter(PrinterSelectorArgument(config))
  }

  private fun resolvePrinter(config: PrinterSelectorArgument): EscPosPrinter? {
    var connection: DeviceConnection? = null
    when (config.connection) {
      PRINTER_CONNECTION_NETWORK -> {
        connection = TcpConnection(config.address, config.port)
      }
      PRINTER_CONNECTION_BLUETOOTH -> {
        connection = BluetoothPrintersConnectionsManager.selectByDeviceAddress(reactContext, config.address)
      }
      PRINTER_CONNECTION_USB -> {
        connection = UsbPrintersConnectionsManager.selectByDeviceName(reactContext, config.address)
      }
      PRINTER_CONNECTION_SERIAL -> {
        connection = SerialConnectionsManager.selectByDeviceName(config.address, config.baudrate)
      }
    }
    if (connection == null) {
      return null
    }
    return EscPosPrinter(
      reactContext,
      connection,
      config.dpi,
      config.width,
      config.maxChars
    )
  }

  private fun emitEventToRNSide(eventName: String, params: WritableMap?) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun emitScanOtherEvent(scanType: Int, event: String, serviceName: String?) {
    val eventParams = Arguments.createMap().apply {
      putInt("scanType", scanType)
    }
    eventParams.putString("event", event)
    eventParams.putString("serviceName", serviceName)
    emitEventToRNSide(DeviceScanner.EVENT_OTHER, eventParams)
  }

  private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val action = intent.action
      if (DeviceScanner.ACTION_USB_PERMISSION == action) {
        synchronized(this) {
          val usbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager?
          val usbDevice = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            emitScanOtherEvent(DeviceScanner.SCAN_USB, "permissionGranted", usbDevice?.deviceName)
          } else {
            emitScanOtherEvent(DeviceScanner.SCAN_USB, "permissionDenied", usbDevice?.deviceName)
          }
        }
      }
    }
  }

  // Required for rn built in EventEmitter Calls.
  @ReactMethod
  fun addListener(eventName: String) {
    if (mListenerCount == 0) {
      Log.d(LOG_TAG, "Does Nothing")
    }
    mListenerCount += 1
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    if (mListenerCount > 0 ) mListenerCount -= count
    if (mListenerCount == 0) {
      Log.d(LOG_TAG, "Does Nothing")
    }
  }

  private fun initWorkerListener() {
    if (currentActivity == null) {
      return
    }
    if (workerListenerInitialized) {
      return
    }
    GlobalScope.launch(Dispatchers.Main) {
      WorkManager.getInstance(reactContext)
        // requestId is the WorkRequest id
        .getWorkInfosByTagLiveData(PrintingWorkerManager.PRINTING_JOB_TAG)
        .observe(currentActivity as LifecycleOwner, Observer { works: List<WorkInfo> ->
          if (works.isNotEmpty()) {
            works.forEach { workInfo ->
              val eventParams = Arguments.createMap().apply {
                workInfo.progress.getString("connection")?.let {
                  putString("connection", it)
                }
                workInfo.progress.getString("address")?.let {
                  putString("address", it)
                }
                workInfo.progress.getInt("port", 0).takeIf { it > 0 }?.let {
                  putInt("port", it)
                }
                workInfo.progress.getInt("baudrate", 0).takeIf { it > 0 }?.let {
                  putInt("baudrate", it)
                }
                workInfo.progress.getInt("dpi", 0).takeIf { it > 0 }?.let {
                  putInt("dpi", it)
                }
                workInfo.progress.getFloat("width", 0f).takeIf { it > 0f }?.let {
                  putDouble("width", it.toDouble())
                }
                workInfo.progress.getInt("maxChars", 0).takeIf { it > 0 }?.let {
                  putInt("maxChars", it)
                }
                workInfo.progress.getString("jobId")?.let {
                  putString("jobId", it)
                }
                workInfo.progress.getString("jobName")?.let {
                  putString("jobName", it)
                }
                workInfo.progress.getString("jobTag")?.let {
                  putString("jobTag", it)
                }
                putString("state", workInfo.state.name)
                putString("id", workInfo.id.toString())
                val tags = Arguments.createArray()
                workInfo.tags.forEach {
                  tags.pushString(it)
                }
                putArray("tags", tags)
                putInt("generation", workInfo.generation)
                putInt("runAttemptCount", workInfo.runAttemptCount)
              }
              when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> {
                  emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                }

                WorkInfo.State.RUNNING -> {
                  emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                }

                WorkInfo.State.SUCCEEDED -> {
                  emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                }

                WorkInfo.State.FAILED -> {
                  val errorMessage = workInfo.outputData.getString("error")
                  val connection = workInfo.progress.getString("connection")
                  val address = workInfo.progress.getString("address")
                  if (errorMessage.isNullOrEmpty() && connection.isNullOrEmpty() && address.isNullOrEmpty()) {
                    eventParams.putString("state", "PENDING")
                    emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                  } else {
                    eventParams.putString("error",errorMessage)
                    emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                  }
                  PrintingWorkerManager.getInstance().cancelWork(reactContext, workInfo.id);
                }

                WorkInfo.State.BLOCKED -> {
                  emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                }

                WorkInfo.State.CANCELLED -> {
                  emitEventToRNSide(EVENT_PRINTING_JOB, eventParams)
                }
              }
            }
          }
        })
    }
    workerListenerInitialized = true
  }

  class PrinterSelectorArgument(argv: Data) {
    constructor(argv: ReadableMap) : this(Data.Builder().putAll(argv.toHashMap()).build())

    val connection: String
    val address: String
    val port: Int
    val baudrate: Int
    val dpi: Int
    val width: Float
    val maxChars: Int
    init {
      connection = argv.getString("connection")!!
      address = argv.getString("address")!!
      port = argv.getInt("port", NetworkScanManager.DEFAULT_PRINTER_PORT)
      baudrate = argv.getInt("baudrate", SerialConnection.DEFAULT_BAUD_RATE)
      dpi = argv.getInt("dpi", PRINTING_DPI_NORMAL)
      width = argv.getFloat("width", PRINTING_WIDTH_80_MM)
      maxChars = argv.getInt("maxChars", PRINTING_LINES_MAX_CHAR_42)
    }

    val data: Data
      get() {
        return Data.Builder()
          .putString("connection", connection)
          .putString("address", address)
          .putInt("port", port)
          .putInt("baudrate", baudrate)
          .putInt("dpi", dpi)
          .putFloat("width", width)
          .putInt("maxChars", maxChars)
          .build()
      }
  }

  private class ScanTypeArgument(argv: ReadableMap) {
    val connection: Int
    init {
      connection = argv.getInt("connection")
    }
  }
}
