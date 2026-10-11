package com.example.scanaggregator

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * 「生成二维码」页：输入文字/网址 → 生成预览 → 保存到相册。
 * 生成用 ZXing 原生 QRCodeWriter；保存兼容 Android 10+ MediaStore 与旧版存储。
 */
class GenerateQrActivity : AppCompatActivity() {

    private lateinit var inputView: EditText
    private lateinit var previewView: ImageView
    private lateinit var saveButton: Button
    private var generated: Bitmap? = null

    // Android 9 及以下写公共相册需要存储权限，运行时申请
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                saveBitmap()
            } else {
                Toast.makeText(this, R.string.generate_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_qr)

        inputView = findViewById(R.id.input_view)
        previewView = findViewById(R.id.preview_view)
        saveButton = findViewById(R.id.save_button)
        saveButton.isEnabled = false

        findViewById<Button>(R.id.generate_button).setOnClickListener { generate() }
        saveButton.setOnClickListener {
            if (generated == null) {
                Toast.makeText(this, R.string.generate_nothing_to_save, Toast.LENGTH_SHORT).show()
            } else {
                onSaveClicked()
            }
        }
    }

    private fun generate() {
        val text = inputView.text.toString().trim()
        if (text.isEmpty()) {
            // 输入为空不生成空白二维码
            Toast.makeText(this, R.string.generate_empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        generated = try {
            encodeQr(text, QR_SIZE)
        } catch (e: Exception) {
            null
        }
        if (generated == null) {
            Toast.makeText(this, R.string.generate_failed, Toast.LENGTH_SHORT).show()
        } else {
            previewView.setImageBitmap(generated)
            saveButton.isEnabled = true
        }
    }

    private fun encodeQr(text: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    private fun onSaveClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ MediaStore 写公共相册无需权限
            saveBitmap()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            saveBitmap()
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun saveBitmap() {
        val bitmap = generated ?: return
        // 压缩写盘放工作线程
        Thread {
            val ok = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveViaMediaStore(bitmap) else saveLegacy(bitmap)
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.generate_saved else R.string.generate_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun saveViaMediaStore(bitmap: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "qr_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScanAggregator")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } ?: return false
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap): Boolean {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ScanAggregator")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return true
    }

    companion object {
        private const val QR_SIZE = 600
    }
}
