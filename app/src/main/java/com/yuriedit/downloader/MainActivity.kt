package com.yuriedit.downloader

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var urlInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var rightsCheck: CheckBox
    private lateinit var downloadButton: Button
    private lateinit var cancelButton: Button
    private lateinit var updateButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView

    @Volatile private var engineReady = false
    @Volatile private var isDownloading = false
    @Volatile private var processId: String? = null
    private var pendingPermissionDownload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)
        setContentView(buildUi())
        setupSelectors()
        setupActions()
        handleSharedText(intent)
        initializeEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 10, 15))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(34))
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "YURI DOWNLOADER"
            setTextColor(Color.rgb(110, 160, 255))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        })
        root.addView(TextView(this).apply {
            text = "MP3 & MP4 Downloader"
            setTextColor(Color.WHITE)
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Bağlantıyı yapıştır, formatı seç ve dosyayı telefonuna kaydet."
            setTextColor(Color.rgb(160, 170, 186))
            textSize = 14f
        })

        val card = verticalCard()
        root.addView(card, lpTop(22))

        card.addView(label("YouTube bağlantısı"))
        val urlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlInput = EditText(this).apply {
            hint = "https://youtu.be/..."
            setHintTextColor(Color.rgb(105, 115, 130))
            setTextColor(Color.WHITE)
            setSingleLine(false)
            minHeight = dp(52)
            setPadding(dp(13), 0, dp(13), 0)
            setBackgroundColor(Color.rgb(13, 17, 25))
        }
        urlRow.addView(urlInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        val pasteButton = button("Yapıştır", false)
        urlRow.addView(pasteButton, LinearLayout.LayoutParams(dp(100), dp(54)).apply { marginStart = dp(9) })
        card.addView(urlRow, lpTop(8))

        card.addView(label("Çıktı türü"), lpTop(18))
        typeSpinner = darkSpinner()
        card.addView(typeSpinner, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        card.addView(label("Video kalitesi"), lpTop(18))
        qualitySpinner = darkSpinner()
        card.addView(qualitySpinner, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        rightsCheck = CheckBox(this).apply {
            text = "Bu içeriği indirme hakkım veya içerik sahibinin açık izni var."
            setTextColor(Color.rgb(180, 190, 205))
            textSize = 13f
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 135, 255))
        }
        card.addView(rightsCheck, lpTop(16))

        downloadButton = button("İndirmeyi Başlat", true).apply { isEnabled = false }
        card.addView(downloadButton, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })

        cancelButton = button("İptal Et", false).apply { visibility = View.GONE }
        card.addView(cancelButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

        val progressCard = verticalCard()
        root.addView(progressCard, lpTop(14))

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusText = TextView(this).apply {
            text = "Hazırlanıyor…"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        progressText = TextView(this).apply {
            text = "0%"
            setTextColor(Color.rgb(67, 135, 255))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        statusRow.addView(progressText)
        progressCard.addView(statusRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 135, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(40, 48, 61))
        }
        progressCard.addView(progressBar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(14) })

        detailText = TextView(this).apply {
            text = "İndirme motoru telefonda hazırlanıyor."
            setTextColor(Color.rgb(160, 170, 186))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        progressCard.addView(detailText)

        updateButton = button("İndirme Motorunu Güncelle", false).apply { isEnabled = false }
        progressCard.addView(updateButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })

        root.addView(TextView(this).apply {
            text = "Dosyalar Downloads/Yuri Downloads klasörüne kaydedilir. Özel veya DRM korumalı içerikler desteklenmez."
            setTextColor(Color.rgb(100, 110, 125))
            textSize = 11f
            gravity = Gravity.CENTER
        }, lpTop(16))

        pasteButton.setOnClickListener { pasteClipboard() }
        return scroll
    }

    private fun setupSelectors() {
        typeSpinner.adapter = spinnerAdapter(listOf("MP4 Video", "MP3 Ses"))
        qualitySpinner.adapter = spinnerAdapter(listOf("En iyi", "2160p", "1440p", "1080p", "720p", "480p", "360p"))
        qualitySpinner.setSelection(3)
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                qualitySpinner.isEnabled = position == 0 && !isDownloading
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupActions() {
        downloadButton.setOnClickListener {
            if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingPermissionDownload = true
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 701)
            } else {
                startDownload()
            }
        }
        cancelButton.setOnClickListener {
            processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            statusText.text = "İptal ediliyor…"
            cancelButton.isEnabled = false
        }
        updateButton.setOnClickListener { updateEngine() }
    }

    private fun initializeEngine() {
        worker.execute {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                } catch (_: Exception) {}
                engineReady = true
                runOnUiThread {
                    statusText.text = "Hazır"
                    detailText.text = "Bağlantıyı yapıştırıp formatı seç."
                    downloadButton.isEnabled = true
                    updateButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Motor başlatılamadı"
                    detailText.text = friendlyError(error.message)
                }
            }
        }
    }

    private fun startDownload() {
        if (!engineReady || isDownloading) return
        val url = extractFirstUrl(urlInput.text.toString()) ?: urlInput.text.toString().trim()
        if (!isYoutubeUrl(url)) {
            urlInput.error = "Geçerli bir YouTube bağlantısı gir."
            return
        }
        if (!rightsCheck.isChecked) {
            toast("İndirme hakkın olduğunu onayla.")
            return
        }

        val tempDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YuriTemp").apply {
            mkdirs()
            listFiles()?.forEach { it.deleteRecursively() }
        }
        val request = buildRequest(url, tempDir)
        val startedAt = System.currentTimeMillis()
        val id = "yuri-$startedAt"
        processId = id
        isDownloading = true
        setBusy(true)
        progressBar.progress = 0
        progressText.text = "0%"
        statusText.text = "Başlatılıyor…"
        detailText.text = "Video bilgileri alınıyor."

        worker.execute {
            try {
                YoutubeDL.getInstance().execute(request, id) { progress, eta, line ->
                    runOnUiThread {
                        val percent = progress.coerceIn(0f, 100f).toInt()
                        progressBar.progress = percent
                        progressText.text = "$percent%"
                        statusText.text = if (line.contains("ffmpeg", true)) "Dönüştürülüyor…" else "İndiriliyor…"
                        detailText.text = if (eta > 0) "Tahmini kalan süre: ${eta} sn" else line.takeLast(180).ifBlank { "İşlem devam ediyor." }
                    }
                }

                val result = tempDir.listFiles()
                    ?.filter { it.isFile && it.lastModified() >= startedAt - 5_000 }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("İndirilen dosya bulunamadı.")

                val publishedName = publishToDownloads(result)
                tempDir.listFiles()?.forEach { it.deleteRecursively() }

                runOnUiThread {
                    progressBar.progress = 100
                    progressText.text = "100%"
                    statusText.text = "Tamamlandı"
                    detailText.text = "$publishedName, Downloads/Yuri Downloads klasörüne kaydedildi."
                    toast("İndirme tamamlandı.")
                }
            } catch (_: YoutubeDL.CanceledException) {
                runOnUiThread {
                    progressBar.progress = 0
                    progressText.text = "0%"
                    statusText.text = "İptal edildi"
                    detailText.text = "İşlem durduruldu."
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "İndirme hatası"
                    detailText.text = friendlyError(error.message)
                }
            } finally {
                processId = null
                isDownloading = false
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun buildRequest(url: String, tempDir: File): YoutubeDLRequest {
        val output = File(tempDir, "%(title).160B [%(id)s].%(ext)s").absolutePath
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--newline")
            .addOption("--add-metadata")
            .addOption("--windows-filenames")
            .addOption("-o", output)

        if (typeSpinner.selectedItemPosition == 1) {
            request
                .addOption("-f", "ba/b")
                .addOption("--extract-audio")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "0")
        } else {
            val height = qualitySpinner.selectedItem?.toString()?.removeSuffix("p")?.toIntOrNull()
            val selector = if (height == null) {
                "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
            } else {
                "bv*[height<=?${height}][ext=mp4]+ba[ext=m4a]/b[height<=?${height}][ext=mp4]/bv*[height<=?${height}]+ba/b[height<=?${height}]/b"
            }
            request
                .addOption("-f", selector)
                .addOption("--merge-output-format", "mp4")
                .addOption("--remux-video", "mp4")
        }
        return request
    }

    private fun publishToDownloads(source: File): String {
        val name = source.name
        val mime = when (source.extension.lowercase(Locale.US)) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Yuri Downloads")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads klasörüne yazılamadı.")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Çıktı dosyası açılamadı.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yuri Downloads").apply { mkdirs() }
            val target = uniqueFile(dir, name)
            FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)))
        }
        return name
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (file.exists()) {
            file = File(dir, if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext")
            index++
        }
        return file
    }

    private fun updateEngine() {
        if (isDownloading) return
        updateButton.isEnabled = false
        downloadButton.isEnabled = false
        statusText.text = "Güncelleniyor…"
        detailText.text = "En güncel yt-dlp sürümü kontrol ediliyor."
        worker.execute {
            try {
                val result = YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                runOnUiThread {
                    statusText.text = "Güncel"
                    detailText.text = result.toString()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Güncelleme yapılamadı"
                    detailText.text = friendlyError(error.message)
                }
            } finally {
                runOnUiThread {
                    updateButton.isEnabled = true
                    downloadButton.isEnabled = true
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        urlInput.isEnabled = !busy
        typeSpinner.isEnabled = !busy
        qualitySpinner.isEnabled = !busy && typeSpinner.selectedItemPosition == 0
        rightsCheck.isEnabled = !busy
        downloadButton.isEnabled = !busy && engineReady
        updateButton.isEnabled = !busy && engineReady
        cancelButton.visibility = if (busy) View.VISIBLE else View.GONE
        cancelButton.isEnabled = busy
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("Panoda bağlantı yok.") else urlInput.setText(extractFirstUrl(text) ?: text)
    }

    private fun handleSharedText(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            val text = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val shared = extractFirstUrl(text)
            if (!shared.isNullOrBlank()) urlInput.setText(shared)
        }
    }

    private fun extractFirstUrl(text: String): String? =
        Regex("""https?://[^\s<>\"']+""", RegexOption.IGNORE_CASE)
            .find(text)?.value?.trimEnd('.', ',', ')', ']', '}')

    private fun isYoutubeUrl(text: String): Boolean = try {
        val uri = Uri.parse(text)
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        uri.scheme in listOf("http", "https") && (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com"))
    } catch (_: Exception) { false }

    private fun friendlyError(raw: String?): String {
        val message = raw.orEmpty()
        val lower = message.lowercase(Locale.US)
        return when {
            "requested format is not available" in lower -> "Bu videoda seçilen kalite bulunamadı. Daha düşük kaliteyi veya En iyi seçeneğini dene."
            "private video" in lower -> "Video özel; erişim kısıtı aşılamaz."
            "sign in" in lower || "login" in lower -> "Bu içerik YouTube hesabıyla giriş gerektiriyor."
            "video unavailable" in lower -> "Video kullanılamıyor veya bölgesel olarak kısıtlı."
            "network" in lower || "connection" in lower -> "İnternet bağlantısını kontrol et."
            message.isBlank() -> "Bilinmeyen bir hata oluştu."
            else -> message.takeLast(650)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && pendingPermissionDownload) {
            pendingPermissionDownload = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDownload()
            else toast("Depolama izni verilmedi.")
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            worker.shutdownNow()
        }
        super.onDestroy()
    }

    private fun verticalCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(18), dp(17), dp(18))
        setBackgroundColor(Color.rgb(20, 25, 34))
        elevation = dp(2).toFloat()
    }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun darkSpinner() = Spinner(this).apply {
        setBackgroundColor(Color.rgb(13, 17, 25))
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun spinnerAdapter(values: List<String>) = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun button(textValue: String, primary: Boolean) = Button(this).apply {
        text = textValue
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setBackgroundColor(if (primary) Color.rgb(55, 120, 245) else Color.rgb(40, 49, 65))
    }

    private fun lpTop(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
