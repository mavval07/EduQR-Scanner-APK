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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var etServerUrl: EditText
    private lateinit var etPin: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnTorch: Button
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
    private var torchOn = false
    private var isSubjectMode = false
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

        httpClient = buildLanHttpClient()

        previewView = findViewById(R.id.previewView)
        etServerUrl = findViewById(R.id.etServerUrl)
        etPin = findViewById(R.id.etPin)
        btnConnect = findViewById(R.id.btnConnect)
        btnTorch = findViewById(R.id.btnTorch)
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

        etServerUrl.setText(prefs.getString("server_url", "http://192.168.1.10:8080"))
        etPin.setText(prefs.getString("server_pin", ""))
        loadCachedSnapshot()
        updateQueueLabel()

        btnConnect.setOnClickListener {
            saveConnectionPrefs()
            connectToPcServer(silent = false)
        }

        btnTorch.setOnClickListener {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            btnTorch.text = if (torchOn) "Flash ON" else "Flash"
        }

        btnModeToggle.setOnClickListener {
            isSubjectMode = !isSubjectMode
            btnModeToggle.text = if (isSubjectMode) "Mode: Mapel" else "Mode: Umum"
            spSubject.visibility = if (isSubjectMode) View.VISIBLE else View.GONE
        }

        btnFlushQueue.setOnClickListener {
            flushOfflineQueue(silent = false)
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
            tvStatusBadge.setBackgroundColor(Color.parseColor("#065F46"))
            tvStatusBadge.setTextColor(Color.parseColor("#A7F3D0"))
        } else {
            tvStatusBadge.setBackgroundColor(Color.parseColor("#78350F"))
            tvStatusBadge.setTextColor(Color.parseColor("#FDE68A"))
        }
    }

    private fun connectToPcServer(silent: Boolean) {
        val baseUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val pin = etPin.text.toString().trim()
        if (baseUrl.isEmpty() || pin.isEmpty()) {
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
                            setConnectedBadge(true, "Terhubung (${studentMap.size} Siswa)")
                            tvResultTitle.text = "Terhubung ke Server PC!"
                            tvResultDetail.text = "Siap memindai kartu QR siswa."
                            flushOfflineQueue(silent = true)
                        } else {
                            setConnectedBadge(false, "PIN Salah")
                            val err = json.optString("error", "HTTP ${it.code}")
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
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, subjectLabels)
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

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
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
                    beepAndVibrate()
                    etServerUrl.setText(parts[1].trim().trimEnd('/'))
                    etPin.setText(parts[2].trim())
                    saveConnectionPrefs()
                    connectToPcServer(silent = false)
                }
            }
            return
        }

        runOnUiThread {
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

    private fun pushScanToPc(qrText: String) {
        val baseUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val pin = etPin.text.toString().trim()
        val label = resolveStudentLabel(qrText)

        if (baseUrl.isEmpty() || pin.isEmpty()) {
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
                    tvResultTitle.text = "Tersimpan di Antrean HP: $label"
                    tvResultDetail.text = "Wi-Fi terputus. Akan otomatis dikirim ke PC saat tersambung."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string() ?: "{}"
                response.use {
                    runOnUiThread {
                        val json = try { JSONObject(respStr) } catch (_: Exception) { JSONObject() }
                        if (it.isSuccessful && json.optBoolean("ok", false)) {
                            setConnectedBadge(true, "Terhubung ke PC")
                            tvResultTitle.text = "Terkirim ke PC: $label"
                            tvResultDetail.text = "Kode: $qrText (${if (isSubjectMode) "Mapel" else "Umum"})"
                        } else {
                            val err = json.optString("error", "HTTP ${it.code}")
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
                            setConnectedBadge(true, "Terhubung ke PC")
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
