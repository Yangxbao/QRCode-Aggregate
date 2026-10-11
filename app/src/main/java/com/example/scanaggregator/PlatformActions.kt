package com.example.scanaggregator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import java.net.URLEncoder

/** 执行规则后得到的结果，MainActivity 据此更新界面。 */
data class ActionResult(
    val message: String,
    val showCopyButton: Boolean,
    val externalOpened: Boolean
)

class PlatformActions(
    private val context: Context,
    private val jumpLauncher: ActivityResultLauncher<Intent>
) {

    fun execute(rule: ScanRule, content: String): ActionResult = when (rule.action) {
        RuleAction.COPY_HINT -> {
            copyToClipboard(context, content)
            ActionResult(
                message = rule.hint ?: rule.tip ?: context.getString(R.string.copied),
                showCopyButton = true,
                externalOpened = false
            )
        }
        RuleAction.SCHEME -> openByScheme(rule, content)
    }

    private fun openByScheme(rule: ScanRule, content: String): ActionResult {
        val uri = Uri.parse(fillTemplate(rule.target ?: "", content))

        // 内置规则：跳转前先做安装检测（包名优先 + scheme 回退的双重校验）。
        // 自定义规则（运行时添加）无法预检，直接尝试唤起
        if (rule.isBuiltIn && !isInstalled(rule, uri)) {
            return notInstalled(rule, content)
        }

        return try {
            // 用 startActivityForResult 机制唤起：外部 App 按返回关闭时，系统会把本 App
            // 自动带回前台，而不是落在桌面或直接退出；返回结果本身不需要处理
            jumpLauncher.launch(Intent(Intent.ACTION_VIEW, uri))
            ActionResult(
                message = rule.tip ?: context.getString(R.string.opening_fmt, rule.name),
                showCopyButton = false,
                externalOpened = true
            )
        } catch (e: Exception) {
            // 自定义规则 scheme 唤起失败时，再尝试按包名打开 App 本体（成功率高得多）
            if (!rule.isBuiltIn && tryLaunchAppByPackage(rule)) {
                ActionResult(
                    message = rule.tip ?: context.getString(R.string.opening_fmt, rule.name),
                    showCopyButton = false,
                    externalOpened = true
                )
            } else {
                // 统一兜底：复制内容 + Toast 提示，不允许闪退
                jumpFailed(rule.isBuiltIn, content)
            }
        }
    }

    /** 快捷启动页用：应用是否已安装（包名优先 + scheme 回退的双重校验） */
    fun isAppInstalled(rule: ScanRule): Boolean = isInstalled(rule, Uri.parse(rule.target ?: ""))

    /**
     * 快捷启动页用：依次尝试 scheme 唤起、按包名唤起 App 本体，全部失败返回 false。
     * 不做任何 UI 提示，由调用方决定失败后的交互（如调试弹窗）。
     */
    fun tryLaunchApp(rule: ScanRule): Boolean {
        if (!rule.target.isNullOrEmpty()) {
            if (tryLaunch(Intent(Intent.ACTION_VIEW, Uri.parse(rule.target)))) return true
        }
        return tryLaunchAppByPackage(rule)
    }

    // 按包名取 App 的桌面启动意图直接唤起，比猜测的 scheme 可靠
    private fun tryLaunchAppByPackage(rule: ScanRule): Boolean {
        val pkg = rule.packageName ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return tryLaunch(intent)
    }

    private fun tryLaunch(intent: Intent): Boolean = try {
        // startActivityForResult 机制：外部 App 返回时系统自动把本 App 带回前台
        jumpLauncher.launch(intent)
        true
    } catch (e: Exception) {
        false
    }

    // 未安装目标应用：Toast 提示 + 复制内容，结果页同步显示原因
    private fun notInstalled(rule: ScanRule, content: String): ActionResult {
        copyToClipboard(context, content)
        val message = context.getString(R.string.not_installed_fmt, rule.name)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return ActionResult(
            message = message,
            showCopyButton = true,
            externalOpened = false
        )
    }

    // 唤起失败统一兜底：复制内容 + Toast 提示，不允许闪退
    private fun jumpFailed(builtin: Boolean, content: String): ActionResult {
        copyToClipboard(context, content)
        val message = context.getString(if (builtin) R.string.jump_failed else R.string.launch_failed_copy)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return ActionResult(
            message = message,
            showCopyButton = true,
            externalOpened = false
        )
    }

    /**
     * 安装检测双重校验：优先包名（getPackageInfo，结果确定），包名未命中再回退
     * scheme 解析（个别 App 的包名配置可能有偏差，scheme 能解析到就别误判）。
     */
    private fun isInstalled(rule: ScanRule, uri: Uri): Boolean {
        rule.packageName?.let { pkg ->
            if (isPackageInstalled(pkg)) return true
        }
        return isSchemeResolvable(uri)
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    // Android 11+ 包可见性：内置 scheme 已在 Manifest <queries> 声明
    private fun isSchemeResolvable(uri: Uri): Boolean = try {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, uri), 0) != null
    } catch (e: Exception) {
        // 检测异常时不拦截，交给跳转的 try/catch 兜底
        true
    }

    private fun fillTemplate(target: String, content: String): String =
        target
            .replace("{url_encoded}", content.urlEncoded())
            .replace("{url}", content)

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("扫码内容", text))
}
