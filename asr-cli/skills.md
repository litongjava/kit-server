# ASR CLI Skill

Use this skill when a user wants to transcribe an audio file with the `asr` command.

## Purpose

`asr` sends an audio file to Gitee ASR and writes the transcription to stdout or an output file.

Default model:

```text
whisper-large-v3
```

Supported models observed in Gitee ASR:

```text
SenseVoiceSmall
MOSS-Audio-8B-Thinking
whisper-large-v3-turbo
whisper-large
GLM-ASR
FunASR
whisper-large-v3
whisper-base
```

Supported output formats:

```text
text
json
verbose_json
srt
vtt
```

## Required Configuration

The command expects `GITEE_API_KEY` to be available from the user's environment or configuration files loaded by `EnvUtils`.

Do not print or expose the real API key. If the key must be provided for a one-off command, use:

```powershell
asr --api-key "your_api_key" -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

## Basic Usage

Show help:

```powershell
asr --help
```

Transcribe to a text file:

```powershell
asr -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

Print transcription to stdout:

```powershell
asr -i "C:/path/to/audio.mp3"
```

Use a specific model:

```powershell
asr --model whisper-large-v3-turbo -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

Use FunASR for Chinese audio:

```powershell
asr --model FunASR -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt" --prompt "Chinese conversation recording. Transcribe only the speech you hear."
```

Generate SRT subtitles:

```powershell
asr --format srt -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.srt"
```

Generate VTT subtitles:

```powershell
asr --format vtt -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.vtt"
```

Use a prompt:

```powershell
asr -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt" --prompt "This is a Chinese phone call recording."
```

## Argument Files

For Chinese paths, spaces, or long Windows paths, prefer an argument file.

Example `asr-args.txt`:

```text
-i
C:/path/to/audio.mp3
-o
C:/path/to/audio.txt
```

Run:

```powershell
asr @asr-args.txt
```

Argument file rules:

- UTF-8 text
- one argument per line
- blank lines are ignored
- lines beginning with `#` are ignored

## Common Parameters

```text
-i, --input <file>          audio file path; positional input is also supported
-o, --output <file>         output file path; stdout is used when omitted
-m, --model <model>         ASR model; default is whisper-large-v3
-f, --format <format>       text/json/verbose_json/srt/vtt; default is text
-p, --prompt <text>         optional transcription prompt
-t, --temperature <number>  optional sampling temperature
    --stream <true|false>   optional stream flag
    --api-key <key>         override GITEE_API_KEY
    --base-url <url>        override GITEE_API_URL
-h, --help                  show help
```

## Usage Notes

If the user provides a Windows path containing Chinese characters, create a UTF-8 argument file and run `asr @args.txt`.

If the command returns `401 Unauthorized`, the API key is missing or invalid.

If the command fails with a network permission error in a sandboxed environment, rerun with network permission or ask for approval.
