package com.example.scanaggregator

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.zxing.BarcodeCameraScanActivity
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.analyze.MultiFormatAnalyzer

/**
 * 扫码页：ZXingLite 的 BarcodeCameraScanActivity 子类。
 * 相机运行时权限由库内部申请；识别成功后把内容带回 MainActivity。
 */
class ScanActivity : BarcodeCameraScanActivity() {

    private lateinit var torchButton: TextView
    private var torchOn = false

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        // 提示音跟随设置页开关（默认关闭）
        val beep = getSharedPreferences(ScanApp.PREFS_SETTINGS, MODE_PRIVATE)
            .getBoolean(ScanApp.KEY_BEEP, false)
        cameraScan.setPlayBeep(beep)
    }

    override fun createAnalyzer(): Analyzer<Result>? {
        // 只识别二维码：按官方建议仅设置 QR_CODE hints，效率更高
        val decodeConfig = DecodeConfig().setHints(DecodeFormatManager.QR_CODE_HINTS)
        return MultiFormatAnalyzer(decodeConfig)
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false) // 停止分析，避免重复回调
        setResult(RESULT_OK, Intent().putExtra(CameraScan.SCAN_RESULT, result.result.text))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addTorchButton()
    }

    // 在预览层右上角叠加闪光灯开关：挂到 content 根布局，不依赖库内部布局 id
    private fun addTorchButton() {
        torchButton = TextView(this).apply {
            text = "🔦"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            // 设备没有闪光灯：置灰不可点击
            torchButton.isEnabled = false
            torchButton.alpha = 0.3f
        } else {
            torchButton.setOnClickListener { toggleTorch() }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(0, 64, 48, 0) }
        (window.decorView.findViewById<ViewGroup>(android.R.id.content)).addView(torchButton, params)
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        try {
            cameraScan.enableTorch(torchOn)
        } catch (e: Exception) {
            torchOn = false
        }
        torchButton.alpha = if (torchOn) 1f else 0.7f
    }

    override fun onDestroy() {
        // 退出扫码页关闭闪光灯，防止一直亮灯
        if (torchOn) {
            try {
                cameraScan.enableTorch(false)
            } catch (e: Exception) {
                // 相机已释放时手电筒会随之关闭
            }
            torchOn = false
        }
        super.onDestroy()
    }
}
