package ru.info_wind.infowind_rfid_android

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URL


class MainActivity : AppCompatActivity() {
    var wifiNetwork: Network? = null
    var socket: Socket? = null
    lateinit var ssidInput: EditText
    lateinit var passInput: EditText
    lateinit var hostInput: EditText
    lateinit var portInput: EditText
    lateinit var heartbeatBtn: Button
    lateinit var lteBtn: Button
    lateinit var wifiBtn: Button
    lateinit var atBtn: Button
    lateinit var versionBtn: Button
    lateinit var syncBtn: Button
    lateinit var scanCountInput: EditText
    lateinit var scanDurationInput: EditText
    lateinit var persistentSw: Switch
    lateinit var scanBtn: Button
    lateinit var findBtn: Button
    lateinit var interruptBtn: Button
    lateinit var downloadBtn: Button
    lateinit var clearBtn: Button
    lateinit var rebootBtn: Button
    lateinit var commandInput: EditText
    lateinit var sendBtn: Button
    lateinit var logView: TextView
    lateinit var statusView: TextView
    lateinit var connectivityManager: ConnectivityManager

    var mainLooperHandler = Handler(Looper.getMainLooper())

    var heartbeatHandler = Handler()
    var heartbeatLogEnabled = false

    var wifiConnectionNeeded = false

    fun setHeartbeatStatusGray() {
        heartbeatBtn.setTextColor(Color.parseColor("#FFAAAAAA"))
    }

    fun setHeartbeatStatusGreen() {
        heartbeatBtn.setTextColor(Color.parseColor("#FF16C60C"))
    }

    fun setHeartbeatStatusRed() {
        heartbeatBtn.setTextColor(Color.parseColor("#FFE81224"))
    }

    fun setLTEStatusGray() {
        lteBtn.setTextColor(Color.parseColor("#FFAAAAAA"))
    }

    fun setLTEStatusGreen() {
        lteBtn.setTextColor(Color.parseColor("#FF16C60C"))
    }

    fun setLTEStatusRed() {
        lteBtn.setTextColor(Color.parseColor("#FFE81224"))
    }

    var lastTimeMessageRecieved: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val packageInfo = applicationContext.getPackageManager()
                .getPackageInfo(applicationContext.getPackageName(), 0)
        title = "InfoWind RFID " + packageInfo.versionName

        log(title.toString())

        ssidInput = findViewById(R.id.ssidInput)
        passInput = findViewById(R.id.passInput)
        hostInput = findViewById(R.id.hostInput)
        portInput = findViewById(R.id.portInput)
        heartbeatBtn = findViewById(R.id.heartbeatBtn)
        lteBtn = findViewById(R.id.lteBtn)
        wifiBtn =  findViewById(R.id.wifiBtn)
        atBtn = findViewById(R.id.atBtn)
        versionBtn = findViewById(R.id.versionBtn)
        syncBtn = findViewById(R.id.syncBtn)
        scanCountInput = findViewById(R.id.scanCountInput)
        scanDurationInput = findViewById(R.id.scanDurationInput)
        persistentSw = findViewById(R.id.persistentSw)
        scanBtn = findViewById(R.id.scanBtn)
        findBtn = findViewById(R.id.findBtn)
        interruptBtn = findViewById(R.id.interruptBtn)
        downloadBtn = findViewById(R.id.downloadBtn)
        clearBtn = findViewById(R.id.clearBtn)
        rebootBtn = findViewById(R.id.rebootBtn)
        commandInput = findViewById(R.id.commandInput)
        sendBtn = findViewById(R.id.sendBtn)
        logView = findViewById(R.id.logView)
        statusView = findViewById(R.id.statusView)

        val preferences = getPreferences(MODE_PRIVATE)
        ssidInput.setText(preferences.getString("ssid", "INFOWIND_RFID_SCANNER"))
        passInput.setText(preferences.getString("pass", ""))
        hostInput.setText(preferences.getString("host", "192.168.4.1"))
        portInput.setText(preferences.getString("port", "55757"))
        commandInput.setText(preferences.getString("command", ""))
        persistentSw.isChecked = preferences.getBoolean("persistent", true)

        logView.setMovementMethod(ScrollingMovementMethod())
        atBtn.setOnClickListener { sendCommand("AT") }
        versionBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+VERSION") })
        interruptBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+INT") })
        findBtn.setOnClickListener(View.OnClickListener { view: View? -> find() })
        scanBtn.setOnClickListener(View.OnClickListener { view: View? -> scan() })
        syncBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+SYNC," + System.currentTimeMillis()) })
        downloadBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+DOWNLOAD") })
        clearBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+CLEAR") })
        rebootBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand("AT+REBOOT") })
        sendBtn.setOnClickListener(View.OnClickListener { view: View? -> sendCommand(commandInput.text.toString()) })
        scanCountInput.setOnLongClickListener(OnLongClickListener { view: View? ->
            scanCountInput.setText("inf")
            false
        })
        scanDurationInput.setOnLongClickListener(OnLongClickListener { view: View? ->
            scanDurationInput.setText("inf")
            false
        })
        logView.setOnLongClickListener(OnLongClickListener { view: View? ->
            AlertDialog.Builder(this)
                    .setMessage("Clear console?")
                    .setPositiveButton("YES") { dialogInterface: DialogInterface?, i: Int -> logView.setText("Cleared\r\n") }
                    .setNegativeButton("CANCEL", null)
                    .show()
            true
        })
        heartbeatBtn.setOnClickListener(View.OnClickListener { heartbeatLogEnabled = !heartbeatLogEnabled })

        wifiBtn.setOnClickListener {
            if (wifiConnectionNeeded == false) {
                wifiBtn.setText("Unlock \uD83D\uDD13")
                wifiConnectionNeeded = true
            } else {
                wifiBtn.setText("Lock \uD83D\uDD12")
                wifiConnectionNeeded = false
            }
            if (networkCallbackConnected) {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                networkCallbackConnected = false
            }
            wifiNetwork = null
        }

        val textWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                savePrefs()
            }
        }
        ssidInput.addTextChangedListener(textWatcher)
        passInput.addTextChangedListener(textWatcher)
        hostInput.addTextChangedListener(textWatcher)
        portInput.addTextChangedListener(textWatcher)
        commandInput.addTextChangedListener(textWatcher)
        persistentSw.setOnCheckedChangeListener { view: View?, isChecked: Boolean -> savePrefs() }

        runConnector()
        setHeartbeatStatusGray()
    }

    fun find() {
        val sb = StringBuilder("AT+FIND")
        if (persistentSw.isChecked) sb.append("?PERSISTENT") else sb.append("?BEST")
        appendParam(sb, "COUNT", scanCountInput.text.toString())
        val duration = appendParam(sb, "DURATION", scanDurationInput.text.toString())
        sendCommand(sb.toString())
    }

    fun scan() {
        val sb = StringBuilder("AT+SCAN")
        appendParam(sb, "COUNT", scanCountInput.text.toString())
        val duration = appendParam(sb, "DURATION", scanDurationInput.text.toString())
        sendCommand(sb.toString())
    }

    fun sendCommand(command: String) {
        try {
            val writerThread = Thread {
                try {
                    val os = socket!!.getOutputStream()
                    os.write((command + "\r\n").toByteArray())
                    os.flush()
                    log(">", command)
                } catch (exc: Exception) {
                    log(exc.javaClass.simpleName, exc.message)
                }
            }
            writerThread.start()
            writerThread.join()
        } catch (exc: Exception) {
            log(exc.javaClass.simpleName, exc.message)
        }
    }

    fun ensureConnection__DEPRECATED__() {
        val connectivityManager = applicationContext.getApplicationContext().getSystemService(
            CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        val networks = connectivityManager.allNetworks
        log(networks.toString())
        for (network in networks) {
            val networkInfo = connectivityManager.getNetworkInfo(network)
            log(networkInfo!!.typeName.toString())
            if (networkInfo!!.type == ConnectivityManager.TYPE_WIFI) {
                wifiNetwork = network // Grabbing the Network object for later usage
            }
        }
    }




    var networkCallbackConnected = false
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            log("onAvailable()", network.toString())
            wifiNetwork = network
        }
        override fun onLost(network: Network) {
            log("onLost()")
            wifiNetwork = null
            connectivityManager.unregisterNetworkCallback(this)
            networkCallbackConnected = false
        }
        override fun onUnavailable() {
            log("onUnavailable()")
            wifiNetwork = null
            connectivityManager.unregisterNetworkCallback(this)
            networkCallbackConnected = false
        }
    }

    fun ensureConnection() {
        if (!networkCallbackConnected) {
            val wfBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssidInput.text.toString())
                .setWpa2Passphrase(passInput.text.toString())
            val nBuilder = NetworkRequest.Builder()
                //.clearCapabilities()
                //.setIncludeOtherUidNetworks(true)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            if (wifiConnectionNeeded)
                nBuilder.setNetworkSpecifier(wfBuilder.build())
            connectivityManager.requestNetwork(nBuilder.build(), networkCallback)
            log("NETWROK_REQUESTED()")
            networkCallbackConnected = true
        }
    }

/*
    fun ensureConnection2(ssid: String) {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val configuration = WifiConfiguration()
        configuration.SSID = "\"" + ssid + "\""
        configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        wifiManager.addNetwork(configuration)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf<String>(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val list: List<WifiConfiguration> = wifiManager.getConfiguredNetworks()
        for (i in list) {
            if (i.SSID != null && i.SSID == "\"" + ssid + "\"") {
                wifiManager.disconnect()
                wifiManager.enableNetwork(i.networkId, true)
                wifiManager.reconnect()
                break
            }
        }


        val builder: NetworkRequest.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = NetworkRequest.Builder()
            builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            connectivityManager.requestNetwork(builder.build(), object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val ssid: String = wifiManager.getConnectionInfo().getSSID()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(null)
                        if (ssid == "\"" + Prefs.getWifiName(this@PUFMainActivity) + "\"") {
                            connectivityManager.bindProcessToNetwork(network)
                            connectivityManager.unregisterNetworkCallback(this)
                        }
                    }
                }
            })
        }
    }

    fun ensureConnection1() {
        val ssid = ssidInput.text.toString()
        val pass = passInput.text.toString()

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        if (wifiManager.connectionInfo.supplicantState == SupplicantState.DISCONNECTED) {
            val requestBuilder = NetworkRequest.Builder()
            requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.requestNetwork(requestBuilder.build(), object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                }
                override fun onUnavailable() {
                    super.onUnavailable()
                }
            })
            /*

            if (ssid.isEmpty() || pass.isEmpty())
                return;

            val conf = WifiConfiguration()
            conf.SSID = "\"" + ssid + "\""
            conf.preSharedKey = "\"" + pass + "\""
            wifiManager.addNetwork(conf)
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf<String>(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return
            }
            var list = wifiManager.configuredNetworks
            for (i in list) {
                if (i.SSID != null && i.SSID == "\"" + ssid + "\"") {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(i.networkId, true)
                    wifiManager.reconnect()
                    break
                }
            }
            */
        }
    }
*/

    fun savePrefs() {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("ssid", ssidInput.text.toString())
        editor.putString("pass", passInput.text.toString())
        editor.putString("host", hostInput.text.toString())
        editor.putString("port", portInput.text.toString())
        editor.putString("command", commandInput.text.toString())
        editor.putBoolean("persistent", persistentSw.isChecked)
        editor.commit()
    }

    var checkLTECounter = 2
    fun checkLTE() {
        checkLTECounter = (checkLTECounter + 1) % 3
        if (checkLTECounter != 0) return
        val url = URL("https://romanovskii.com/internet_connection")
        val urlConnection = url.openConnection() as HttpURLConnection
        urlConnection.connectTimeout = 2500
        try {
            val isr = InputStreamReader(urlConnection.inputStream, "UTF-8")
            val br = BufferedReader(isr)
            var line = br.readLine()
            while (line != null) {
                if (line.startsWith("internet_connected")) {
                    runUI { setLTEStatusGreen() }
                }
                line = br.readLine()
            }
        } catch (ex: Exception) {
            runUI { setLTEStatusRed() }
        } finally {
            urlConnection.disconnect()
        }
    }

    fun socketCloseSafe() {
        try { socket!!.close() } catch (ex: Exception) {}
        stopParser()
        socket = null
    }
    fun runConnector() {
        var connectorThread = Thread {
            while (true) {
                try {
                    checkLTE()

                    if (hostInput.text.toString().isEmpty() || portInput.text.toString().isEmpty()) continue
                    val host = hostInput.text.toString()
                    val port = portInput.text.toString().toInt()
                    val addr: SocketAddress = InetSocketAddress(host, port)

                    val now = System.currentTimeMillis()

                    if (socket == null) {
                        // handle later
                    } else if (socket!!.isClosed || !socket!!.isConnected || !socket!!.isBound || socket!!.isInputShutdown || socket!!.isOutputShutdown) {
                        status("Closing dead connection")
                        socketCloseSafe()
                    } else if (socket!!.remoteSocketAddress != addr) {
                        status("Reconnecting", "from", socket!!.remoteSocketAddress.toString(), "to", addr.toString())
                        socketCloseSafe()
                    } else if (now - lastTimeMessageRecieved > 5000) {
                        status("Silent for 5 seconds, reconnect")
                        lastTimeMessageRecieved = now
                        socketCloseSafe()
                    } else if (wifiNetwork == null) {
                        status("Lost wifi network, reconnecting")
                        socketCloseSafe()
                    } else {
                        // all good
                    }

                    if (wifiNetwork == null) {
                        ensureConnection()
                    }
                    if (wifiNetwork == null && wifiConnectionNeeded) {
                        status("Connection not ensured")
                        continue
                    }

                    if (socket == null) {
                        status("Connecting to", host, Integer.toString(port))
                        socket = Socket()
                        try {
                            wifiNetwork!!.bindSocket(socket)
                        } catch (exc: Exception) {
                            status("Can't bind socket")
                            socket = null
                            continue
                        }
                        socket!!.connect(addr, 5000)
                        socket!!.soTimeout = 5000
                        runParser()
                        status("Connected to", host, Integer.toString(port))
                    }


                } catch (exc: ConnectException) {
                    status("Connection failed")
                    stopParser()
                    socket = null
                }  catch (exc: SocketTimeoutException) {
                    status("Socket timed out")
                    stopParser()
                    socket = null
                }  catch (exc: Exception) {
                    status(exc.javaClass.simpleName, exc.message, exc.stackTraceToString())
                    stopParser()
                    socket = null
                } finally {
                    Thread.sleep(1000)
                }
            }
        }
        connectorThread.start()
    }

    var parserOnline = false
    var parserThread: Thread? = null
    fun runParser() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        parserThread = Thread {
            heartbeatHandler.removeCallbacksAndMessages(null)
            runUI { setHeartbeatStatusRed() }
            parserOnline = true
            try {
                val `is` = socket!!.getInputStream()
                val isr = InputStreamReader(`is`, "UTF-8")
                val br = BufferedReader(isr)
                while (parserOnline) {
                    if (br.ready()) {
                        val line = br.readLine()
                        lastTimeMessageRecieved = System.currentTimeMillis()
                        if (line == "+HB") {
                            heartbeatHandler.removeCallbacksAndMessages(null)
                            runUI { setHeartbeatStatusGreen() }
                            heartbeatHandler.postDelayed({ runUI { setHeartbeatStatusRed() } }, 1500)
                            if (heartbeatLogEnabled) log("<", line)
                        } else {
                            log("<", line)
                        }
                    }
                }
            } catch (e: IOException) {
                log("Cant use the socket")
            }
        }
        parserThread!!.start()
    }

    fun stopParser() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        runUI { setHeartbeatStatusGray() }
        if (parserThread == null) return
        parserOnline = false
        try {
            parserThread!!.join()
        } catch (exc: InterruptedException) {
            log("Error stopping parser thread", exc.toString())
        }
    }

    fun log(arg: String?, vararg args: String?) {
        runUI {
            logView.append(arg)
            for (more in args) {
                logView.append(" ")
                logView.append(more ?: "null")
            }
            logView.append("\n")
        }
    }

    fun status(arg: String?, vararg args: String?) {
        log(arg, *args)
        runUI {
            statusView.setText(arg)
            for (more in args) {
                statusView.append(" ")
                statusView.append(more ?: "null")
            }
        }
    }

    fun runUI(r: Runnable) {
        //runOnUiThread(r);
        mainLooperHandler.post(r)
        //new Handler(Looper.getMainLooper()).post(r);
    }

    fun appendParam(sb: StringBuilder, name: String, value: String): Int? {
        val first = sb.indexOf("?") == -1
        return if (value == "inf") {
            sb.append(if (first) '?' else '&')
                    .append(name)
                    .append("=inf")
            Int.MAX_VALUE
        } else try {
            val number = Integer.valueOf(value)
            sb.append(if (first) '?' else '&')
                    .append(name)
                    .append("=")
                    .append(number)
            number
        } catch (err: NumberFormatException) {
            null
        }
    }
}