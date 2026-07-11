# ASR CLI

`asr-cli` 是一个基于 Gitee ASR 接口的命令行工具，用于将 mp3 等音频文件转写为文本或字幕文件。

默认模型：

```text
whisper-large-v3
```

支持输出格式：

```text
text
json
verbose_json
srt
vtt
```

## 产物

本模块可以编译出两种命令形式：

```text
asr-cli/target/asr-cli-1.0.0-cli.jar
asr-cli/target/asr.exe
```

`asr-cli-1.0.0-cli.jar` 需要 JVM 运行。

`asr.exe` 是 GraalVM Native Image 编译出的 Windows 原生命令，不需要额外启动 JVM。

## 配置

CLI 会调用 `EnvUtils.load(args)`，会读取用户目录和当前目录下的配置文件。

常用配置：

```properties
GITEE_API_KEY=your_api_key
GITEE_API_URL=https://ai.gitee.com/v1
```

也可以在命令行中临时覆盖：

```powershell
asr --api-key "your_api_key" -i "audio.mp3" -o "audio.txt"
```

不要把真实 API Key 提交到 Git。

## 编译 Fat Jar

在项目根目录执行：

```powershell
mvn -q -pl asr-cli -am package -DskipTests
```

输出：

```text
asr-cli/target/asr-cli-1.0.0-cli.jar
```

验证：

```powershell
java -jar asr-cli/target/asr-cli-1.0.0-cli.jar --help
```

## 编译 Native 命令

Windows 下 GraalVM Native Image 需要：

- GraalVM JDK 21
- Visual Studio Build Tools
- MSVC `cl.exe`

工程的 native 编译参数在 `asr-cli/pom.xml` 中配置：

```xml
<graalvm.home>...</graalvm.home>
<msvc.vcvarsall>...</msvc.vcvarsall>
<msvc.cl.exe>...</msvc.cl.exe>
```

如果本机安装路径不同，修改这些属性即可。

编译：

```powershell
mvn -q -pl asr-cli -am -Pnative verify -DskipTests
```

输出：

```text
asr-cli/target/asr.exe
```

验证：

```powershell
asr-cli/target/asr.exe --help
```

## 使用编译后的命令

### 直接运行

在项目根目录中运行：

```powershell
asr-cli/target/asr.exe -i "audio.mp3" -o "audio.txt"
```

使用完整路径也可以：

```powershell
C:/path/to/project/asr-cli/target/asr.exe -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

### 添加到 PATH

可以把 `asr.exe` 复制到固定命令目录，并把该目录添加到系统 `PATH`。

示例：

```powershell
mkdir C:/tools/bin
Copy-Item asr-cli/target/asr.exe C:/tools/bin/asr.exe
```

重新打开 PowerShell 后：

```powershell
asr --help
```

转写文本：

```powershell
asr -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

指定模型：

```powershell
asr --model whisper-large-v3-turbo -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

输出字幕：

```powershell
asr --format srt -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.srt"
```

## 中文路径和长路径

Windows 中文路径、长路径建议使用参数文件，避免 shell 编码问题。

示例 `asr-args.txt`：

```text
-i
C:/path/to/audio.mp3
-o
C:/path/to/audio.txt
```

执行：

```powershell
asr @asr-args.txt
```

参数文件规则：

- UTF-8 编码
- 每行一个参数
- 空行会被忽略
- 以 `#` 开头的行会被忽略

## 参数

```text
-i, --input <file>          音频文件路径，也支持位置参数
-o, --output <file>         输出文件路径；不指定时输出到 stdout
-m, --model <model>         ASR 模型，默认 whisper-large-v3
-f, --format <format>       text/json/verbose_json/srt/vtt，默认 text
-p, --prompt <text>         可选提示词
-t, --temperature <number>  可选采样温度
    --stream <true|false>   可选 stream 参数
    --api-key <key>         覆盖配置中的 GITEE_API_KEY
    --base-url <url>        覆盖配置中的 GITEE_API_URL
-h, --help                  显示帮助
```

## 常见问题

### Failed to find vcvarsall.bat

说明 Visual Studio Build Tools 未安装，或 `pom.xml` 中的 `msvc.vcvarsall` 路径不正确。

可检查：

```powershell
Get-ChildItem -Path "C:/Program Files (x86)/Microsoft Visual Studio" -Recurse -Filter vcvarsall.bat
```

### cl.exe not found

说明 MSVC 编译器未安装，或 `pom.xml` 中的 `msvc.cl.exe` 路径不正确。

可检查：

```powershell
Get-ChildItem -Path "C:/Program Files (x86)/Microsoft Visual Studio" -Recurse -Filter cl.exe
```

### 401 Unauthorized

说明没有读取到有效的 `GITEE_API_KEY`。

检查配置文件或使用 `--api-key` 临时覆盖。

### PowerShell profile warning

如果编译时看到类似提示：

```text
profile.ps1 cannot be loaded because running scripts is disabled
```

只要 Maven 返回码是 `0`，产物已生成，这个提示不影响编译结果。
