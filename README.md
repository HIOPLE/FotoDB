<div align="center">

<img src="app/src/main/res/drawable-nodpi/icon.png" alt="FotoDB 图标" width="88" />

# FotoDB

<p>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" /></a>
  <a href="https://github.com/hiople/FotoDB/actions/workflows/auto-release.yml"><img src="https://github.com/hiople/FotoDB/actions/workflows/auto-release.yml/badge.svg" alt="构建状态" /></a>
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-orange.svg" alt="Min SDK: 24" />
</p>

**建立文字—图片映射数据库，让表情包和梗图检索更简单。**

FotoDB 是一款面向表情包、梗图和个人图片素材的 Android 端图片管理工具。它为图片建立描述、标签索引并支持 OCR 识别，为图片附加可以搜索的文字信息，帮助你快速找到想用的图片。

</div>

## 目录

- [功能亮点](#功能亮点)
- [快速开始](#快速开始)
- [使用说明](#使用说明)
- [数据与文件操作](#数据与文件操作)
- [构建项目](#构建项目)
- [项目结构](#项目结构)

## 功能亮点

| 功能 | 说明 |
| --- | --- |
| 📁 文件夹索引 | 使用系统文件选择器添加一个或多个目录，递归扫描图片并建立索引。 |
| 🗃️ 文字—图片数据库 | 为每张图片保存文件名、描述、标签和 OCR 文字，建立可检索的文字—图片映射。 |
| 🔤 端侧 OCR | 手动识别单张图片中的中文文字，编辑结果后写入描述。 |
| 😂 表情包与梗图检索 | 在搜索框输入图片中的文字、梗的关键词或自定义描述，快速找到对应图片素材；支持正则表达式提交搜索。|
| 🖼️ 图片浏览 | 网格或列表浏览；支持 PNG、JPEG 和 GIF，文件类型会显示在图片条目中；全屏查看支持缩放、拖动、旋转和适配屏幕，GIF 在全屏查看时会播放动画。 |
| 📤 图片分享 | 通过 Android 系统分享面板将图片分享给其他应用。 |

## 快速开始

1. 安装并启动 FotoDB。
2. 点击左上角菜单，选择 **添加文件夹**。
3. 在 Android 系统文件选择器中选择图片目录并授予访问权限。
4. 等待扫描完成，即可浏览和搜索图片。

## 使用说明

- 在顶部搜索框输入关键词，可按文件名、描述或标签查找图片。
- 点击 **正则按钮**切换正则模式；输入表达式后按键盘上的搜索/回车键提交。
- 在菜单中切换网格/列表视图、刷新图片或管理已添加的文件夹。
- 点击图片可编辑描述和标签；停止输入约 500 毫秒后自动保存，窗口会显示待保存、保存中、保存成功或失败状态；关闭编辑窗口时会立即提交尚未完成的修改。
- 在编辑窗口可手动触发端侧文字识别。OCR 结果会先供编辑，可选择替换或追加到描述；应用后会按描述的自动保存机制写入。
- 点击图片预览或 **查看大图**可进入全屏查看；网格和列表条目会显示 PNG、JPEG 或 GIF 类型，GIF 在全屏查看时以动画播放。

## 数据与文件操作

FotoDB 使用 Android Storage Access Framework 访问用户主动选择的目录，并在本地 SQLite 数据库中保存图片索引、描述和标签。目录 URI 与显示偏好保存在应用偏好设置中，缩略图位于应用私有缓存目录。

文字识别使用 Google ML Kit 端侧中文文本识别模型，仅在用户手动触发单张图片识别时运行。识别结果可以修改后写入描述，用于后续的表情包和梗图检索。

图片索引支持 PNG、JPEG 和 GIF。列表中的类型优先根据 MIME 类型判断，并在 MIME 缺失或不匹配时依据文件扩展名回退；全屏查看时 GIF 会播放动画，且保留缩放、拖动和旋转操作。

在图片编辑窗口点击 **删除图片** 会从原文件夹中永久删除图片，同时移除本地索引。该操作不可撤销；删除前应用会显示确认提示。移除文件夹则只删除该文件夹的索引并释放访问授权，不会删除文件夹中的原始图片。

> [!WARNING]
> “删除图片”会实际删除原文件；如果只想让图片不出现在数据库中，请移除对应文件夹后重新选择其他目录，或先备份重要图片。

## 构建项目

需要 Android Studio 和项目配置所需的 Android SDK。项目使用 Gradle Kotlin DSL，应用模块为 `app`。

```bash
# 构建 Debug APK
./gradlew :app:assembleDebug

# 安装到已连接的设备
./gradlew :app:installDebug
```

### 项目配置

- **最低 Android 版本：** API 24
- **Compile SDK：** API 37
- **Target SDK：** API 37
- **Java 语言级别：** Java 11

## 项目结构

```text
app/src/
├── main/
│   ├── java/com/hiople/fotodb/
│   │   ├── MainActivity.java       # 主界面、扫描、搜索、浏览与分享
│   │   ├── ImageDatabase.java      # SQLite 图片索引及描述/标签数据
│   │   ├── OcrImageLoader.java     # OCR 输入图片解码与方向处理
│   │   └── OcrTextUtils.java       # OCR 文本整理工具
│   └── res/                        # 图标、主题及其他 Android 资源
└── test/                           # 单元测试
```
