# ScanAggregator — 扫码聚合 App | QRCode Aggregate

[简体中文](README.md) | [English](README_EN.md)

一个纯本地实现的 Android 二维码聚合工具：相机 / 相册识码 → 本地规则识别平台 → Scheme 唤起或复制链接。支持自定义识别规则、应用快捷启动、历史记录、二维码生成，**无任何网络权限**。

当前版本 1.1.0 · [下载 APK（Releases）](https://github.com/Yangxbao/QRCode-Aggregate/releases) · [开源协议 MIT](LICENSE)

## 功能清单

- 相机扫码：ZXingLite 实时识别，右上角闪光灯开关（无闪光灯设备置灰，退出自动关灯）
- 相册识码：系统 Photo Picker 选图解码，免存储权限
- 扫码模式：自动识别（规则引擎）/ 指定应用 / App内扫一扫（只复制链接）
- 应用快捷启动：36 个内置应用 + 本机扫描添加的自定义应用，点击直接唤起，失败弹调试弹窗（可复制 Scheme）
- 平台识别规则引擎：规则集中在 `assets/rules.json`，新增应用只改 JSON 不改代码
- App 安装检测：包名优先 + scheme 回退双重校验
- 二维码生成：文字 / 网址 → 实时预览 → 保存到相册
- 历史记录：自动保存、关键词搜索、导出 txt、长按菜单、多选批量删除
- 结果页：内容类型识别（网址 / WiFi / 纯文本等）、复制文本、一键分享
- Scheme 测试页、深浅色主题切换、关于页（版本 / 构建时间 / 项目链接）

## 下载安装（普通用户）

1. 打开 [Releases](https://github.com/Yangxbao/QRCode-Aggregate/releases) 页，下载最新版 `ScanAggregator-vX.X.X.apk`；
2. 手机上点开安装，系统提示"未知来源 / 允许安装"时确认；
3. 首次扫码需允许相机权限；相册识码、保存图片、导出记录均无需存储权限。

## 项目截图

TODO：发布前补充真机截图（首页 / 扫码页 / 应用快捷启动 / 历史记录 / 生成二维码）。

## 编译环境

- Android Studio Ladybug（2024.2.1）或更新版本
- AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.9（Wrapper 自动下载）
- compileSdk 35 / minSdk 24（Android 7.0）
- 建议真机调试（模拟器相机扫真实二维码不方便）

## 开发者编译教程

1. 克隆仓库：`git clone https://github.com/Yangxbao/QRCode-Aggregate.git`
2. Android Studio「Open」打开项目根目录，等待 Gradle Sync 完成（首次自动下载依赖，国内网络可按下方提示换镜像）；
3. 连接真机（开发者模式 + USB 调试），点击 Run 'app'；
4. 命令行构建：`gradlew.bat :app:assembleDebug`（Windows）或 `./gradlew :app:assembleDebug`，产物在 `app/build/outputs/apk/debug/`；
5. 自定义识别规则：编辑 `app/src/main/assets/rules.json`（见下文格式说明）；
6. 新增内置应用后，请在 Manifest `<queries>` 里同步补充对应 scheme 与 package。

### 自定义规则格式（简写风格）

```json
{
  "keyword": "weixin.com|wechat.com",
  "tip": "识别到微信链接",
  "appScheme": "weixin://",
  "package": "com.tencent.mm"
}
```

- `keyword`：命中关键词，`|` 分隔可配多个，contains 忽略大小写；也兼容 `patterns` 数组
- `appScheme`：跳转 URI（等价于 `target`），支持 `{url}` / `{url_encoded}` 占位符
- `package`：应用包名，安装检测优先使用
- `tip`：命中提示；下拉显示名自动从 tip 推导，也可用 `name` 显式指定
- 规则按数组顺序匹配，第一条命中的生效

## 扫码模式

首页「扫码模式」显示为「当前模式：XXX」，点击弹出选择对话框（点击条目自动收起），选项 = **自动识别 + App内扫一扫 + 全部应用**：

- **自动识别**（默认）：按规则表关键词匹配，命中即执行对应动作（内置应用带安装检测）；未命中则显示原文，并出现"手动选择平台跳转"区域；
- **指定应用（选择某个应用条目）**：不做关键词匹配，扫码成功后直接按该应用的规则处理——scheme 唤起（失败自动复制链接）、copy_hint 类应用执行复制提示；
- **App内扫一扫**：只复制链接到剪贴板并提示手动粘贴，不关联具体应用。

扫码页右上角有闪光灯开关（🔦）：点击开/关手电筒；无闪光灯的设备按钮置灰；退出扫码页自动关灯。

## 应用快捷启动 与 扫描本机应用

顶部「应用快捷启动」进入独立页，展示内置 36 应用 + 自定义应用，**点击条目直接尝试唤起**：

- **内置应用**：先做安装检测（未安装 Toast「未检测到XX应用」）；已安装直接 scheme 跳转；
- **自定义应用**：跳过检测直接唤起（scheme 失败自动按包名唤起 App 本体）；全部失败弹出**调试弹窗**——展示应用名与 Scheme 文本，提供【复制Scheme】按钮与常见原因提示；
- 右上角「扫描本机应用」：自动扫描本机可启动应用（需要 `QUERY_ALL_PACKAGES` 权限），内置 36 个置灰不可选，其余勾选保存为自定义规则——
  自动生成 `tip`（应用名）、`appScheme`（启发式检测：包名分段候选 + 解析验证）、`keyword`（`*` 通配）。

## 生成二维码

首页「生成二维码」进入：输入文字/网址 → 点「生成」实时预览（ZXing `QRCodeWriter`，UTF-8）→ 点「保存图片」存到相册 `Pictures/ScanAggregator`（Android 10+ MediaStore 免权限，Android 9 及以下运行时申请存储权限）；输入为空不生成。

## 内置应用规则（36 条）

规则表在 `app/src/main/assets/rules.json`，改规则只改配置文件、不动代码。每条规则命中跳转时页面会显示对应 tip：

| 规则 | 匹配关键词（contains，忽略大小写） | Scheme | 包名 |
| --- | --- | --- | --- |
| 学习通 | `mobilelearn.chaoxing.com` | `chaoxing://` | `com.chaoxing.mobile` |
| 支付宝 | `qr.alipay.com` | `alipays://` | `com.eg.android.AlipayGphone` |
| 微信 | `weixin.com`、`wechat.com` | `weixin://` | `com.tencent.mm` |
| B站 | `bilibili.com` | `bilibili://` | `tv.danmaku.bili` |
| 抖音 | `douyin.com` | `snssdk1128://` | `com.ss.android.ugc.aweme` |
| 饿了么 | `ele.me` | `eleme://` | `me.ele` |
| 钉钉 | `dingtalk.com` | `dingtalk://` | `com.alibaba.android.rimet` |
| 雷神加速器 | `leigod.com` | `leigod://` | `com.leigod.android.accelerator` |
| 谷歌登录 | `google.com` | `google://` | `com.google.android.googlequicksearchbox` |
| 百度网盘 | `pan.baidu.com` | `baidunetdisk://` | `com.baidu.netdisk` |
| 豆包登录 | `doubao.com` | `doubao://` | `com.larus.nova` |
| Steam登录 | `steamcommunity.com`、`steampowered.com` | `steam://` | `com.valvesoftware.android.steam.community` |
| QQ登录 | `qq.com` | `mqq://` | `com.tencent.mobileqq` |
| 小红书 | `xiaohongshu.com` | `xhs://` | `com.xingin.xhs` |
| 知乎 | `zhihu.com` | `zhihu://` | `com.zhihu.android` |
| 网易云音乐 | `music.163.com` | `orpheus://` | `com.netease.cloudmusic` |
| 阿里云盘 | `shturl.cc/` | `alipan://` | `com.alicloud.databox` |
| TapTap | `taptap.com` | `taptap://` | `com.taptap` |
| 米哈游 | `mihoyo.com` | `mihoyobbs://` | `com.mihoyo.hyperion` |
| 酷狗音乐 | `kugou.com` | `kugou://` | `com.kugou.android` |
| 酷我音乐 | `kuwo.cn` | `kuwo://` | `cn.kuwo.player` |
| 哔哩哔哩 | `bilibili.com` | `bilibili://` | `tv.danmaku.bili` |
| 铁路12306 | `12306.cn` | `cn.12306://` | `com.MobileTicket` |
| 京东 | `jd.com` | `jd://` | `com.jingdong.app.mall` |
| 淘宝 | `taobao.com` | `taobao://` | `com.taobao.taobao` |
| 拼多多 | `pinduoduo.com` | `pinduoduo://` | `com.xunmeng.pinduoduo` |
| 抖音 | `douyin.com` | `snssdk1128://` | `com.ss.android.ugc.aweme` |
| 快手 | `kuaishou.com` | `kwai://` | `com.smile.gifmaker` |
| 微博 | `sina.com.cn` | `sinaweibo://` | `com.sina.weibo` |
| 小米商城 | `xm.com` | `mi://` | `com.xiaomi.shop` |
| 华为应用 | `huawei.com` | `huawei://` | `com.huawei.appmarket` |
| 腾讯视频 | `v.qq.com` | `tenvideo://` | `com.tencent.qqlive` |
| 爱奇艺 | `iqiyi.com` | `iqiyi://` | `com.qiyi.video` |
| 优酷 | `youku.com` | `youku://` | `com.youku.phone` |
| 腾讯文档 | `tencent.com` | `txdoc://` | `com.tencent.docs` |
| 飞书 | `feishu.cn` | `lark://` | `com.ss.android.lark` |

注意：`bilibili.com`、`douyin.com` 各存在两条规则（按需求保留），自动识别按顺序取第一条命中，功能等价，可自行去重。

## App 安装检测

跳转前先做安装检测，采用**包名优先 + scheme 回退的双重校验**：

1. 优先按包名查询（`getPackageInfo`，结果最可靠）；
2. 包名未命中时回退 scheme 解析检测（`resolveActivity`），避免包名偏差造成误判；
3. 判定未安装 → Toast「未检测到XX应用，仅复制链接」并自动复制内容；判定已安装 → 执行 scheme 跳转，跳转失败走统一兜底（复制 + Toast）。

Android 11+ 的包可见性限制要求在 Manifest `<queries>` 里声明 scheme 与 package（当前全部已声明）；新增规则请同步补声明。

## 历史记录

每次扫码解析成功（相机或相册）自动保存：二维码内容、时间、当时模式、匹配到的应用名。存储用 SharedPreferences（JSON 数组），新记录在前，最多 100 条，纯本地。

- 顶部搜索框按关键词实时过滤；
- 点任意一条 → 复制内容；长按 → 菜单【复制链接 / 使用当前模式重新处理 / 删除】；
- 「导出记录」→ 全部记录导出 txt（Android 10+ 存到 `Download/ScanAggregator`，旧版本存应用专属目录）；
- 「多选」→ 勾选多条一键批量删除；每条右侧「删除」删单条；「清空全部」带确认。

## 结果页其他功能

- 内容类型识别：网址 / WiFi信息 / 电话号码 / 短信 / 邮箱 / 纯文本；
- 「复制文本」常驻按钮 + 「分享」系统分享面板。

## Scheme测试 与 设置

- 「Scheme测试」：输入任意 Scheme 链接（只输 scheme 名自动补 `://`）尝试唤起；失败弹出统一调试弹窗（展示 Scheme、可复制）；
- 「设置」：识别成功后自动复制内容（默认关）、扫码提示音（默认关），立即生效并持久保存。

## 主题切换与关于页

- 首页右上角「切换深色 / 切换浅色」，选择持久保存；
- 「关于」页展示版本号（含 versionCode）、构建时间、GitHub 项目链接、内置规则数量与项目说明。

## 相册识码

见「下载安装」与功能清单；解码用 ZXing 原生 `MultiFormatReader`（大图降采样 + 旋转重试），工作线程执行。

## 已知限制

- 受安卓系统与第三方 App 厂商限制，外部 scheme 仅能拉起 App 本体，**无法直接打开其内部扫一扫页面**；跳转失败会自动复制链接并提示；
- 自定义应用（运行时添加）无法做安装状态检测（Android 11+ 系统限制），会直接尝试唤起；
- 自定义应用的 scheme 靠启发式检测（包名分段候选 + 解析验证），命中率有限，检测不到的应用只能走复制流程；
- `QUERY_ALL_PACKAGES` 权限用于「扫描本机应用」，上架应用市场时需提供说明。

## FAQ

**Q：为什么有些 App 点了没反应或提示跳转失败？**
A：多数是厂商限制或该 App 未注册对应 scheme 处理器。可用失败弹窗复制 Scheme，或用「仅复制 / App内扫一扫」复制内容后手动打开 App 粘贴。

**Q：能直接打开 App 内部的扫一扫吗？**
A：不能。安卓系统不提供跨应用直达内部页面的通用方案，这是系统与厂商限制。

**Q：提示"未检测到XX应用"但手机上明明装了？**
A：内置规则的包名可能与你的 App 版本不一致。可在 `rules.json` 里修正对应条目的 `package` 字段；检测有 scheme 回退，一般不会误判。

**Q：扫描本机应用里找不到某个 App 的 Scheme？**
A：公开 API 无法枚举 App 注册的 scheme，采用包名分段启发式检测，命中率有限。可在勾选后手动编辑 SharedPreferences，或等待后续版本增加公共 scheme 映射表。

**Q：生成的二维码和导出的记录保存在哪里？**
A：二维码图片在相册 `Pictures/ScanAggregator`；历史导出在 `Download/ScanAggregator`（Android 10+）。

**Q：为什么这个 App 不联网？**
A：设计如此——识别、规则、历史全部本地完成，Manifest 未声明 INTERNET 权限。

**Q：Gradle Sync 报 JDK 版本错误？**
A：Gradle 8.9 需要 JDK 17~22 运行。系统装了 JDK 25 时，在 AS 设置里把 Gradle JDK 指到 AS 自带 JBR 或 `.jdks` 里的 JDK 17/21 即可。

## 更新日志

### v1.1.0（2026-10-11）
- 结果页「复制文本」常驻；设置页新增「识别成功后自动复制」「扫码提示音」开关
- 历史记录：关键词搜索、导出 txt、多选批量删除
- 新增「Scheme测试」页；关于页展示版本、构建时间与项目链接
- 安装检测升级：包名优先 + scheme 回退双重校验；自定义应用 scheme 失败自动按包名唤起

### v1.0.0
- 界面重构：扫码模式与应用快捷启动分离；模式下拉精简
- 扫码页新增闪光灯开关（无闪光灯设备置灰，退出自动关灯）
- 新增「生成二维码」：文字/网址生成预览并保存到相册
- 唤起失败调试弹窗（展示 Scheme、可复制）

### v0.9.x
- 下拉快捷跳转：点击「XX - 跳转App」直接唤起 App
- 「扫描本机应用」勾选添加自定义应用（keyword 通配，scheme 启发式检测）
- 安装检测升级：包名优先 + scheme 回退；自定义应用按包名兜底唤起

### v0.8.0
- 每个应用拆分「跳转App / App内扫一扫」；内置应用扩至 36 条
- 历史记录长按菜单、结果页分享、深浅色主题、关于页、内容类型识别

### v0.7.0
- 平台识别规则引擎（rules.json 独立配置）、App 安装检测、扫码历史记录、相册识码

## 开源协议

[MIT](LICENSE)
