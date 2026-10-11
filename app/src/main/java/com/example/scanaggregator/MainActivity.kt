package com.example.scanaggregator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.king.camera.scan.CameraScan

class MainActivity : AppCompatActivity() {

    // 扫码模式：自动识别 / 指定应用（扫码后按选中应用处理）/ App内扫一扫（只复制）
    private enum class ScanMode { AUTO, APP, SCAN_IN_APP }

    private lateinit var modeSelector: TextView
    private lateinit var themeToggle: TextView
    private lateinit var contentTypeView: TextView
    private lateinit var resultView: TextView
    private lateinit var messageView: TextView
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var manualSection: View
    private lateinit var platformSpinner: Spinner
    private lateinit var manualJumpButton: Button

    private lateinit var ruleEngine: RuleEngine
    private lateinit var platformActions: PlatformActions
    private var rules: List<ScanRule> = emptyList()

    private var scanMode = ScanMode.AUTO
    private var selectedRule: ScanRule? = null // 仅 APP 模式下使用
    private var lastContent: String? = null
    private var warnedRulesEmpty = false

    // 扫码页（ScanActivity）返回后在这里拿到结果；无返回数据表示用户退出了扫码页
    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val content = result.data?.getStringExtra(CameraScan.SCAN_RESULT)
            if (content == null) {
                Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                handleScanResult(content)
            }
        }

    // 唤起外部 App 走 startActivityForResult：外部 App 按返回关闭时，系统会把本 App
    // 自动带回前台，而不是落在桌面或直接退出；返回结果本身无需处理
    private val jumpLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // 相册选图用系统 Photo Picker：无需存储权限，旧设备自动回退到文档选择器
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) decodeFromGallery(uri)
        }

    // 从历史记录页返回时，用当前模式重新处理该链接（不重复记历史）
    private val reprocessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringExtra(HistoryActivity.EXTRA_REPROCESS)
                ?.let { handleScanResult(it, recordHistory = false) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modeSelector = findViewById(R.id.mode_selector)
        themeToggle = findViewById(R.id.theme_toggle)
        contentTypeView = findViewById(R.id.content_type_text)
        resultView = findViewById(R.id.result_text)
        messageView = findViewById(R.id.message_text)
        copyButton = findViewById(R.id.copy_button)
        shareButton = findViewById(R.id.share_button)
        manualSection = findViewById(R.id.manual_section)
        platformSpinner = findViewById(R.id.platform_spinner)
        manualJumpButton = findViewById(R.id.manual_jump_button)

        ruleEngine = RuleEngine(rules)
        platformActions = PlatformActions(this, jumpLauncher)
        setupPlatformSpinner()

        copyButton.setOnClickListener {
            lastContent?.let { content ->
                copyToClipboard(this@MainActivity, content)
                Toast.makeText(this@MainActivity, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
        shareButton.setOnClickListener { shareResult() }
        manualJumpButton.setOnClickListener { jumpManually() }
        modeSelector.setOnClickListener { showModeSelector() }
        findViewById<Button>(R.id.scan_button).setOnClickListener { startScan() }
        findViewById<Button>(R.id.gallery_button).setOnClickListener {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<TextView>(R.id.quick_launch_entry).setOnClickListener {
            startActivity(Intent(this, QuickLaunchActivity::class.java))
        }
        findViewById<TextView>(R.id.history_entry).setOnClickListener {
            reprocessLauncher.launch(Intent(this, HistoryActivity::class.java))
        }
        findViewById<TextView>(R.id.about_entry).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<TextView>(R.id.generate_entry).setOnClickListener {
            startActivity(Intent(this, GenerateQrActivity::class.java))
        }
        findViewById<TextView>(R.id.scheme_test_entry).setOnClickListener {
            startActivity(Intent(this, SchemeTestActivity::class.java))
        }
        findViewById<TextView>(R.id.settings_entry).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        themeToggle.setOnClickListener { toggleTheme() }
        updateThemeToggle()
        // 必须初始化模式选择框文案：空文本会让控件塌缩成极小触摸区，表现为"点击无反应"
        updateModeSelector()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到首页都重载规则表，保证自定义应用增删后下拉与自动识别立即生效
        reloadRules()
    }

    private fun reloadRules() {
        rules = RuleConfigLoader.loadAll(this)
        ruleEngine = RuleEngine(rules)
        setupPlatformSpinner()

        // 自定义规则增删后，按 id 重新对齐当前选中应用；被移除则回退自动识别
        selectedRule?.let { current -> selectedRule = rules.firstOrNull { it.id == current.id } }
        if (scanMode == ScanMode.APP && selectedRule == null) {
            scanMode = ScanMode.AUTO
        }
        updateModeSelector()

        if (rules.isEmpty()) {
            if (!warnedRulesEmpty) {
                Toast.makeText(this, R.string.rules_load_failed, Toast.LENGTH_LONG).show()
                warnedRulesEmpty = true
            }
        } else {
            warnedRulesEmpty = false
        }
    }

    private fun startScan() {
        // 扫码页是 ZXingLite 的 BarcodeCameraScanActivity 子类，相机权限由库内部申请
        scanLauncher.launch(Intent(this, ScanActivity::class.java))
    }

    private fun handleScanResult(content: String, recordHistory: Boolean = true) {
        lastContent = content
        resultView.text = content
        contentTypeView.text = getString(R.string.content_type_fmt, getString(detectContentType(content)))
        contentTypeView.visibility = View.VISIBLE
        shareButton.visibility = View.VISIBLE
        copyButton.visibility = View.VISIBLE

        val rule = when (scanMode) {
            ScanMode.APP -> selectedRule // 不做关键词匹配，直接按所选应用处理
            ScanMode.AUTO -> ruleEngine.match(content)
            ScanMode.SCAN_IN_APP -> null
        }
        if (recordHistory) {
            HistoryStore.add(this, content, modeLabel(), rule?.name)
        }
        // 设置开启「自动复制」时，识别成功自动复制内容（App内扫一扫模式本身就会复制）
        if (scanMode == ScanMode.AUTO &&
            getSharedPreferences(ScanApp.PREFS_SETTINGS, MODE_PRIVATE)
                .getBoolean(ScanApp.KEY_AUTO_COPY, false)
        ) {
            copyToClipboard(this, content)
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        when {
            scanMode == ScanMode.SCAN_IN_APP -> copyForScanInApp(content)
            rule == null -> showManualSection()
            else -> applyActionResult(platformActions.execute(rule, content))
        }
    }

    private fun modeLabel(): String = when (scanMode) {
        ScanMode.AUTO -> getString(R.string.mode_auto)
        ScanMode.APP -> getString(R.string.mode_specific)
        ScanMode.SCAN_IN_APP -> getString(R.string.mode_scan_in_app)
    }

    // App内扫一扫模式：只复制到剪贴板，不做任何跳转，方便手动打开对应 App 粘贴
    private fun copyForScanInApp(content: String) {
        copyToClipboard(this, content)
        applyActionResult(
            ActionResult(
                message = getString(R.string.scan_in_app_done),
                showCopyButton = true,
                externalOpened = false
            )
        )
    }

    private fun shareResult() {
        val content = lastContent ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    // 二维码内容类型识别（展示用）
    private fun detectContentType(content: String): Int = when {
        content.startsWith("WIFI:", ignoreCase = true) -> R.string.type_wifi
        content.startsWith("http://", ignoreCase = true) ||
            content.startsWith("https://", ignoreCase = true) -> R.string.type_url
        content.startsWith("tel:", ignoreCase = true) -> R.string.type_phone
        content.startsWith("smsto:", ignoreCase = true) ||
            content.startsWith("sms:", ignoreCase = true) -> R.string.type_sms
        content.startsWith("mailto:", ignoreCase = true) -> R.string.type_email
        else -> R.string.type_text
    }

    private fun decodeFromGallery(uri: android.net.Uri) {
        // 解码放工作线程，避免大图卡主线程；结果回主线程后与相机扫码走同一处理
        Thread {
            val content = GalleryDecoder.decode(this, uri)
            runOnUiThread {
                if (content == null) {
                    Toast.makeText(this, R.string.gallery_no_qr, Toast.LENGTH_SHORT).show()
                } else {
                    handleScanResult(content)
                }
            }
        }.start()
    }

    // 模式选择对话框：自动识别 / App内扫一扫 / 各应用（含自定义），点击后自动收起
    private fun showModeSelector() {
        val options = mutableListOf(
            getString(R.string.mode_auto),
            getString(R.string.mode_scan_in_app)
        )
        options.addAll(rules.map { it.name })
        AlertDialog.Builder(this)
            .setTitle(R.string.mode_prompt)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> scanMode = ScanMode.AUTO
                    1 -> { scanMode = ScanMode.SCAN_IN_APP; selectedRule = null }
                    else -> {
                        scanMode = ScanMode.APP
                        selectedRule = rules.getOrNull(which - 2)
                    }
                }
                updateModeSelector()
            }
            .show()
    }

    private fun updateModeSelector() {
        modeSelector.text = getString(R.string.mode_selector_fmt, currentOptionLabel())
    }

    private fun currentOptionLabel(): String = when (scanMode) {
        ScanMode.AUTO -> getString(R.string.mode_auto)
        ScanMode.SCAN_IN_APP -> getString(R.string.mode_scan_in_app)
        ScanMode.APP -> selectedRule?.name ?: getString(R.string.mode_specific)
    }

    // 未识别平台：显示原文，让用户从下拉列表选平台手动跳转
    private fun showManualSection() {
        messageView.visibility = View.GONE
        manualSection.visibility = View.VISIBLE
    }

    private fun applyActionResult(actionResult: ActionResult) {
        manualSection.visibility = View.GONE
        messageView.text = actionResult.message
        messageView.visibility = View.VISIBLE
    }

    private fun setupPlatformSpinner() {
        platformSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rules.map { it.name }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun jumpManually() {
        val content = lastContent ?: return
        val rule = rules.getOrNull(platformSpinner.selectedItemPosition) ?: return
        applyActionResult(platformActions.execute(rule, content))
    }

    // 主题切换：切换后 Activity 自动重建，按钮文案在 onCreate 里刷新
    private fun toggleTheme() {
        val next = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        getSharedPreferences(ScanApp.PREFS_SETTINGS, MODE_PRIVATE)
            .edit().putInt(ScanApp.KEY_NIGHT_MODE, next).apply()
        AppCompatDelegate.setDefaultNightMode(next)
    }

    private fun updateThemeToggle() {
        themeToggle.text = getString(
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                R.string.theme_to_light
            } else {
                R.string.theme_to_dark
            }
        )
    }
}
