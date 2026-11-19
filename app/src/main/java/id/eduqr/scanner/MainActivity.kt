package id.eduqr.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scannerOverlay: ScannerOverlayView
    private lateinit var layoutServerConfig: LinearLayout
    private lateinit var layoutSubjectRow: LinearLayout
    private lateinit var tvCompactServerInfo: TextView
    private lateinit var tvCameraHintPill: TextView
    private lateinit var btnToggleConfig: Button
    private lateinit var etServerUrl: EditText
    private lateinit var etPin: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnTorch: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnZoomToggle: Button
    private lateinit var btnModeToggle: Button
    private lateinit var spSubject: Spinner
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultDetail: TextView
    private lateinit var tvQueueInfo: TextView
    private lateinit var btnFlushQueue: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var camera: Camera? = null
    private var useFrontCamera = false
    private var torchOn = false
    private var zoomIndex = 0
    private val zoomLevels = floatArrayOf(1.0f, 1.5f, 2.0f)
    private var isSubjectMode = false
    private var isConfigExpanded = true
    private var lastQr = ""
    private var lastQrTime = 0L

    private val studentMap = mutableMapOf<String, String>()
    private val subjectIds = mutableListOf<String>()
    private val subjectLabels = mutableListOf<String>()

    private lateinit var httpClient: OkHttpClient
    private lateinit var clientId: String

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Izin kamera wajib diaktifkan.", Toast.LENGTH_LONG).show()
        }

    private val retryRunnable = object : Runnable {
        override fun run() {
            flushOfflineQueue(silent = true)
            mainHandler.postDelayed(this, 3500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val headerContainer = findViewById<LinearLayout>(R.id.headerContainer)
        val footerContainer = findViewById<LinearLayout>(R.id.footerContainer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootContainer)) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val d = resources.displayMetrics.density
            headerContainer.setPadding(
                (14 * d).toInt(),
                sysBars.top + (10 * d).toInt(),
                (14 * d).toInt(),
                (10 * d).toInt()
            )
            footerContainer.setPadding(
                (14 * d).toInt(),
                (10 * d).toInt(),
                (14 * d).toInt(),
                sysBars.bottom + (12 * d).toInt()
            )
            insets
        }

        httpClient = buildLanHttpClient()

        previewView = findViewById(R.id.previewView)
        scannerOverlay = findViewById(R.id.scannerOverlay)
        layoutServerConfig = findViewById(R.id.layoutServerConfig)
        layoutSubjectRow = findViewById(R.id.layoutSubjectRow)
        tvCompactServerInfo = findViewById(R.id.tvCompactServerInfo)
        tvCameraHintPill = findViewById(R.id.tvCameraHintPill)
        btnToggleConfig = findViewById(R.id.btnToggleConfig)
        etServerUrl = findViewById(R.id.etServerUrl)
        etPin = findViewById(R.id.etPin)
        btnConnect = findViewById(R.id.btnConnect)
        btnTorch = findViewById(R.id.btnTorch)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnZoomToggle = findViewById(R.id.btnZoomToggle)
        btnModeToggle = findViewById(R.id.btnModeToggle)
        spSubject = findViewById(R.id.spSubject)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultDetail = findViewById(R.id.tvResultDetail)
        tvQueueInfo = findViewById(R.id.tvQueueInfo)
        btnFlushQueue = findViewById(R.id.btnFlushQueue)

        val prefs = getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
        clientId = prefs.getString("client_id", null) ?: "apk_${UUID.randomUUID().toString().take(8)}".also {
            prefs.edit().putString("client_id", it).apply()
        }

        useFrontCamera = prefs.getBoolean("use_front_camera", false)
        etServerUrl.setText(prefs.getString("server_url", "https://192.168.1.100:8443"))
        etPin.setText(prefs.getString("server_pin", ""))
        updateCameraLensUi()
        updateCompactHeaderText()
        loadCachedSnapshot()
        updateQueueLabel()

        btnToggleConfig.setOnClickListener {
            setServerConfigExpanded(!isConfigExpanded)
        }

        btnConnect.setOnClickListener {
            saveConnectionPrefs()
            updateCompactHeaderText()
            connectToPcServer(silent = false)
        }

        btnSwitchCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            torchOn = false
            zoomIndex = 0
            btnZoomToggle.text = "1.0x"
            prefs.edit().putBoolean("use_front_camera", useFrontCamera).apply()
            updateCameraLensUi()
            startCamera()
        }

        btnTorch.setOnClickListener {
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            if (!hasFlash) {
                Toast.makeText(this, "Kamera yang aktif tidak memiliki lampu Flash.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            updateTorchUi(hasFlash = true)
        }

        btnZoomToggle.setOnClickListener {
            zoomIndex = (zoomIndex + 1) % zoomLevels.size
            val requestedRatio = zoomLevels[zoomIndex]
            val zoomState = camera?.cameraInfo?.zoomState?.value
            val maxRatio = zoomState?.maxZoomRatio ?: 2.0f
            val minRatio = zoomState?.minZoomRatio ?: 1.0f
            val appliedRatio = requestedRatio.coerceIn(minRatio, maxRatio)
            camera?.cameraControl?.setZoomRatio(appliedRatio)
            btnZoomToggle.text = String.format(Locale.US, "%.1fx", appliedRatio)
        }

        btnModeToggle.setOnClickListener {
            isSubjectMode = !isSubjectMode
            btnModeToggle.text = if (isSubjectMode) "Mapel" else "Umum"
            btnModeToggle.setBackgroundColor(
                Color.parseColor(if (isSubjectMode) "#0D9488" else "#4F46E5")
            )
            layoutSubjectRow.visibility = if (isSubjectMode) View.VISIBLE else View.GONE
        }

        btnFlushQueue.setOnClickListener {
            flushOfflineQueue(silent = false)
        }

        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
            true
        }

        if (etPin.text.toString().isNotBlank()) {
            connectToPcServer(silent = true)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        mainHandler.postDelayed(retryRunnable, 3500L)
    }

    private fun updateCameraLensUi() {
        if (useFrontCamera) {
            btnSwitchCamera.text = "Kamera: Dpn"
            btnSwitchCamera.setBackgroundColor(Color.parseColor("#7C3AED"))
            tvCameraHintPill.text = "Kamera Depan • Arahkan Kartu QR ke Layar HP"
        } else {
            btnSwitchCamera.text = "Kamera: Blk"
            btnSwitchCamera.setBackgroundColor(Color.parseColor("#0284C7"))
            tvCameraHintPill.text = "Kamera Belakang • Arahkan ke QR Pairing / Kartu Siswa"
        }
    }

    private fun updateTorchUi(hasFlash: Boolean) {
        if (!hasFlash) {
            torchOn = false
            btnTorch.text = "Flash"
            btnTorch.alpha = 0.45f
            btnTorch.setBackgroundColor(Color.parseColor("#334155"))
        } else {
            btnTorch.alpha = 1.0f
            btnTorch.text = if (torchOn) "Flash ON" else "Flash"
            btnTorch.setBackgroundColor(Color.parseColor(if (torchOn) "#D97706" else "#334155"))
        }
    }

    private fun setServerConfigExpanded(expanded: Boolean) {
        isConfigExpanded = expanded
        layoutServerConfig.visibility = if (expanded) View.VISIBLE else View.GONE
        btnToggleConfig.text = if (expanded) "Tutup" else "Atur"
    }

    private fun updateCompactHeaderText() {
        val url = etServerUrl.text.toString().trim()
        val pin = etPin.text.toString().trim()
        tvCompactServerInfo.text = if (url.isNotEmpty() && pin.isNotEmpty()) {
            "$url • PIN $pin"
        } else {
            "Wi-Fi LAN ke Server PC"
        }
    }

    private fun buildLanHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun saveConnectionPrefs() {
        val cleanUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val cleanPin = etPin.text.toString().trim()
        getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE).edit()
            .putString("server_url", cleanUrl)
            .putString("server_pin", cleanPin)
            .apply()
    }

    private fun setConnectedBadge(connected: Boolean, label: String) {
        tvStatusBadge.text = label
        if (connected) {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_connected)
            tvStatusBadge.setTextColor(Color.parseColor("#A7F3D0"))
        } else {
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning)
            tvStatusBadge.setTextColor(Color.parseColor("#FDE68A"))
        }
    }

    private fun connectToPcServer(silent: Boolean) {
        val baseUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val pin = etPin.text.toString().trim()
        if (baseUrl.isEmpty() || pin.isEmpty()) {
            setServerConfigExpanded(true)
            if (!silent) Toast.makeText(this, "Isi Alamat Server PC & PIN 4-digit.", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject().apply {
            put("pin", pin)
            put("clientId", clientId)
            put("deviceName", "APK ${Build.MANUFACTURER} ${Build.MODEL}")
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/api/client-connect")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setConnectedBadge(false, "Terputus")
                    if (!silent) {
                        tvResultTitle.setTextColor(Color.parseColor("#FBBF24"))
                        tvResultTitle.text = "Gagal Terhubung ke Server PC"
                        tvResultDetail.text = "Pastikan HP & PC di Wi-Fi yang sama (${e.message})"
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: "{}"
                response.use {
                    runOnUiThread {
                        val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                        if (it.isSuccessful && json.optBoolean("ok", false)) {
                            val snap = json.optJSONObject("snapshot")
                            if (snap != null) {
                                getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
                                    .edit().putString("cached_snapshot", snap.toString()).apply()
                                parseSnapshot(snap)
                            }
                            updateCompactHeaderText()
                            setConnectedBadge(true, "Terhubung (${studentMap.size} Siswa)")
                            setServerConfigExpanded(false)
                            tvResultTitle.setTextColor(Color.parseColor("#34D399"))
                            tvResultTitle.text = "Terhubung ke Server PC!"
                            tvResultDetail.text = "Siap memindai kartu QR siswa."
                            flushOfflineQueue(silent = true)
                        } else {
                            setConnectedBadge(false, "PIN Salah")
                            setServerConfigExpanded(true)
                            val err = json.optString("error", "HTTP ${it.code}")
                            tvResultTitle.setTextColor(Color.parseColor("#F87171"))
                            tvResultTitle.text = "Pairing Ditolak PC"
                            tvResultDetail.text = err
                        }
                    }
                }
            }
        })
    }

    private fun loadCachedSnapshot() {
        val raw = getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
            .getString("cached_snapshot", null) ?: return
        try {
            parseSnapshot(JSONObject(raw))
        } catch (_: Exception) {
        }
    }

    private fun parseSnapshot(snap: JSONObject) {
        studentMap.clear()
        val students = snap.optJSONArray("students") ?: JSONArray()
        for (i in 0 until students.length()) {
            val s = students.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val nis = s.optString("nis")
            val name = s.optString("name")
            val cls = s.optString("className")
            val qr = s.optString("qrPayload")
            val display = "$name ($cls)"
            if (id.isNotEmpty()) studentMap[id] = display
            if (nis.isNotEmpty()) studentMap[nis.lowercase()] = display
            if (qr.isNotEmpty()) studentMap[qr] = display
        }

        subjectIds.clear()
        subjectLabels.clear()
        val subjects = snap.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subjects.length()) {
            val sub = subjects.optJSONObject(i) ?: continue
            subjectIds.add(sub.optString("id"))
            subjectLabels.add("${sub.optString("code")} - ${sub.optString("name")}")
        }
        if (subjectLabels.isEmpty()) {
            subjectIds.add("")
            subjectLabels.add("Ikuti Mapel Aktif di PC")
        }
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, subjectLabels).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_dark)
        }
        spSubject.adapter = adapter
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val scanner = BarcodeScanning.getClient()
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                analyzeFrame(scanner, proxy)
            }

            val preferredSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val fallbackSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                provider.unbindAll()
                val selector = if (provider.hasCamera(preferredSelector)) {
                    preferredSelector
                } else {
                    useFrontCamera = !useFrontCamera
                    updateCameraLensUi()
                    fallbackSelector
                }
                camera = provider.bindToLifecycle(this, selector, preview, analysis)
                val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                updateTorchUi(hasFlash)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera gagal dibuka: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeFrame(scanner: com.google.mlkit.vision.barcode.BarcodeScanner, proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { barcodes ->
                for (b in barcodes) {
                    val value = b.rawValue?.trim() ?: continue
                    if (b.format == Barcode.FORMAT_QR_CODE || value.isNotEmpty()) {
                        onQrDetected(value)
                        break
                    }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onQrDetected(rawText: String) {
        val now = System.currentTimeMillis()
        if (rawText == lastQr && (now - lastQrTime) < 2500L) return
        lastQr = rawText
        lastQrTime = now

        if (rawText.startsWith("EDUQRPAIR|")) {
            val parts = rawText.split("|")
            if (parts.size >= 3) {
                runOnUiThread {
                    scannerOverlay.flashScanSuccess()
                    beepAndVibrate()
                    etServerUrl.setText(parts[1].trim().trimEnd('/'))
                    etPin.setText(parts[2].trim())
                    saveConnectionPrefs()
                    updateCompactHeaderText()
                    connectToPcServer(silent = false)
                }
            }
            return
        }

        runOnUiThread {
            scannerOverlay.flashScanSuccess()
            beepAndVibrate()
            pushScanToPc(rawText)
        }
    }

    private fun resolveStudentLabel(qrText: String): String {
        studentMap[qrText]?.let { return it }
        studentMap[qrText.lowercase()]?.let { return it }
        if (qrText.startsWith("EDUQR|")) {
            val p = qrText.split("|")
            if (p.size >= 3) {
                studentMap[p[2]]?.let { return it }
                studentMap[p[1].lowercase()]?.let { return it }
            }
        }
        return qrText
    }

    private fun currentClock(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun pushScanToPc(qrText: String) {
        val baseUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val pin = etPin.text.toString().trim()
        val label = resolveStudentLabel(qrText)
        val timeStr = currentClock()

        if (baseUrl.isEmpty() || pin.isEmpty()) {
            setServerConfigExpanded(true)
            tvResultTitle.setTextColor(Color.parseColor("#FBBF24"))
            tvResultTitle.text = "PIN / Server Belum Diatur"
            tvResultDetail.text = "Scan QR Pairing di layar PC atau isi IP & PIN terlebih dahulu."
            return
        }

        val selectedSubId = if (isSubjectMode && spSubject.selectedItemPosition in subjectIds.indices) {
            subjectIds[spSubject.selectedItemPosition]
        } else ""

        val itemJson = JSONObject().apply {
            put("pin", pin)
            put("clientId", clientId)
            put("deviceName", "APK ${Build.MANUFACTURER} ${Build.MODEL}")
            put("mode", if (isSubjectMode) "subject" else "general")
            put("subjectId", selectedSubId)
            put("qrText", qrText)
        }

        val req = Request.Builder()
            .url("$baseUrl/api/scan-push")
            .post(itemJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                enqueueOfflineItem(itemJson)
                runOnUiThread {
                    setConnectedBadge(false, "Offline (Antre)")
                    tvResultTitle.setTextColor(Color.parseColor("#FBBF24"))
                    tvResultTitle.text = "Antre Offline: $label"
                    tvResultDetail.text = "$timeStr • Wi-Fi terputus, otomatis dikirim saat tersambung."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string() ?: "{}"
                response.use {
                    runOnUiThread {
                        val json = try { JSONObject(respStr) } catch (_: Exception) { JSONObject() }
                        if (it.isSuccessful && json.optBoolean("ok", false)) {
                            setConnectedBadge(true, "Terhubung (${studentMap.size} Siswa)")
                            tvResultTitle.setTextColor(Color.parseColor("#34D399"))
                            tvResultTitle.text = "Terkirim ke PC: $label"
                            tvResultDetail.text = "$timeStr • ${if (isSubjectMode) "Absensi Mapel" else "Absensi Umum"} • $qrText"
                        } else {
                            val err = json.optString("error", "HTTP ${it.code}")
                            tvResultTitle.setTextColor(Color.parseColor("#F87171"))
                            tvResultTitle.text = "Ditolak Server PC"
                            tvResultDetail.text = err
                        }
                    }
                }
            }
        })
    }

    private fun enqueueOfflineItem(item: JSONObject) {
        val prefs = getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("offline_queue", "[]")) } catch (_: Exception) { JSONArray() }
        arr.put(item)
        prefs.edit().putString("offline_queue", arr.toString()).apply()
        runOnUiThread { updateQueueLabel() }
    }

    private fun updateQueueLabel() {
        val prefs = getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("offline_queue", "[]")) } catch (_: Exception) { JSONArray() }
        tvQueueInfo.text = "Antrean offline: ${arr.length()} scan"
    }

    private fun flushOfflineQueue(silent: Boolean) {
        val baseUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val pin = etPin.text.toString().trim()
        val prefs = getSharedPreferences("EduQrScannerPrefs", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("offline_queue", "[]")) } catch (_: Exception) { JSONArray() }
        if (arr.length() == 0 || baseUrl.isEmpty() || pin.isEmpty()) {
            updateQueueLabel()
            return
        }

        val first = arr.optJSONObject(0) ?: return
        first.put("pin", pin)

        val req = Request.Builder()
            .url("$baseUrl/api/scan-push")
            .post(first.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!silent) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "PC belum terjangkau.", Toast.LENGTH_SHORT).show() }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val nextArr = JSONArray()
                        for (i in 1 until arr.length()) nextArr.put(arr.get(i))
                        prefs.edit().putString("offline_queue", nextArr.toString()).apply()
                        runOnUiThread {
                            updateQueueLabel()
                            setConnectedBadge(true, "Terhubung (${studentMap.size} Siswa)")
                            if (nextArr.length() > 0) flushOfflineQueue(silent)
                            else if (!silent) Toast.makeText(this@MainActivity, "Semua antrean terkirim ke PC!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun beepAndVibrate() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95).startTone(ToneGenerator.TONE_PROP_BEEP, 130)
        } catch (_: Exception) {
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(90)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(retryRunnable)
        cameraExecutor.shutdown()
    }
}
