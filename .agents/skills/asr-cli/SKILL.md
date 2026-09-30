---
name: asr-cli
description: Use when transcribing audio with asr-cli, or when changing that tool — covers the asr command, its sync and async endpoints, Whisper response formats, subtitle output, exit codes, and Windows path pitfalls.
---

# ASR CLI

`asr` sends an audio file to Gitee ASR and writes the transcription to stdout or an
output file.

Module README: [`asr-cli/README.md`](../../../asr-cli/README.md).
Repository-wide build and configuration: load the `ai-tools` skill.

## Command and artifacts

```text
asr-cli/target/asr-cli.jar    JVM, needs java -jar
asr-cli/target/asr.exe        Windows native, built only with -Pnative
```

Both modules shade to `target/<artifactId>.jar`, so the JVM jar is always
`asr-cli/target/asr-cli.jar`.

## Model

Default model:

```text
whisper-large-v3
```

Model names observed on Gitee ASR:

```text
SenseVoiceSmall
MOSS-Audio-8B-Thinking
whisper-large-v3-turbo
whisper-large
GLM-ASR
FunASR
Fun-ASR-Nano-2512
whisper-large-v3
whisper-base
```

## Output formats

```text
text
json
verbose_json
srt
vtt
```

`text` is the default, and the CLI extracts the `text` field from the JSON response, so
plain `-o audio.txt` yields readable text. `srt` and `vtt` produce subtitle files.

## Usage

Show help:

```powershell
java -jar asr-cli/target/asr-cli.jar --help
```

Transcribe to a text file:

```powershell
java -jar asr-cli/target/asr-cli.jar -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

Print to stdout:

```powershell
java -jar asr-cli/target/asr-cli.jar -i "C:/path/to/audio.mp3"
```

Specific model, plus a language hint for Chinese audio:

```powershell
java -jar asr-cli/target/asr-cli.jar --model Fun-ASR-Nano-2512 --language zh -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

Subtitles:

```powershell
java -jar asr-cli/target/asr-cli.jar --format srt -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.srt"
java -jar asr-cli/target/asr-cli.jar --format vtt -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.vtt"
```

Guided transcription:

```powershell
java -jar asr-cli/target/asr-cli.jar -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt" --prompt "This is a Chinese phone call recording."
```

Async endpoint, for long audio:

```powershell
java -jar asr-cli/target/asr-cli.jar --async true --poll-interval 10 --async-timeout 3600 -i "C:/path/to/audio.mp3" -o "C:/path/to/audio.txt"
```

## Argument files

For Chinese paths, spaces, or long Windows paths, prefer an argument file.

Example `asr-args.txt`:

```text
-i
C:/path/to/audio.mp3
-o
C:/path/to/audio.txt
```

```powershell
java -jar asr-cli/target/asr-cli.jar @asr-args.txt
```

Argument file rules: UTF-8 text, one argument per line, blank lines ignored, lines
beginning with `#` ignored.

Chinese paths passed inline on a Windows console are corrupted, because the console code
page is not UTF-8. Add UTF-8 output encoding to read Chinese messages on stderr:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
```

## Parameters

```text
-i, --input <file>          audio file path; positional input is also supported
-o, --output <file>         output file path; stdout is used when omitted
-m, --model <model>         ASR model; default is whisper-large-v3
-f, --format <format>       text/json/verbose_json/srt/vtt; default is text
                            --response-format is accepted as an alias
-l, --language <lang>       optional language code, for example zh
-p, --prompt <text>         optional transcription prompt
-t, --temperature <number>  optional sampling temperature
    --stream <true|false>   optional stream flag passed to the API
    --async <true|false>    use the async audio transcription endpoint
    --poll-interval <sec>   async polling interval; default is 5
    --async-timeout <sec>   async timeout; default is 1800
    --api-key <key>         override GITEE_API_KEY
    --base-url <url>        override GITEE_API_URL
-h, --help                  show help
```

## Exit codes

```text
0  success
1  invalid arguments, or the result file could not be written
2  the transcription request or the remote task failed
```

## Failure notes

- `401 Unauthorized` means `GITEE_API_KEY` was missing or invalid.
- A failed sync request prints `Transcription failed, status code: <code>` and the raw
  body, then exits `2`.
- `--async true` polls the task endpoint until `--async-timeout` seconds elapse; a
  `status: failure` payload is reported as an async failure rather than a timeout.
- A sandbox that blocks outbound network access fails before any API call; rerun with
  network permission instead of changing the command.
