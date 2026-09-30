# OCR CLI Skill

Use this skill when a user wants to parse a document or image with the `ocr-cli` command.

## Purpose

`ocr-cli` sends a document/image file to Gitee document processing and writes OCR output to stdout or an output file.

Default model:

```text
PaddleOCR-VL
```

Supported output formats:

```text
markdown
text
json
```

API request fields mirrored from the Python example:

```text
model=PaddleOCR-VL
include_image=true
include_image_base64=true
end_pages=0
output_format=md
```

## Required Configuration

The command expects `GITEE_API_KEY` to be available from the user's environment or configuration files loaded by `EnvUtils`.

## Basic Usage

Show help:

```powershell
ocr-cli --help
```

Parse to Markdown:

```powershell
ocr-cli -i "C:/path/to/document.pdf" -o "C:/path/to/document.md"
```

Use raw JSON output:

```powershell
ocr-cli --format json -i "C:/path/to/image.png" -o "C:/path/to/result.json"
```

Override API request fields explicitly:

```powershell
ocr-cli -i "C:/path/to/document.pdf" --output-format md --include-image true --include-image-base64 true --end-pages 0
```

## Argument Files

For Chinese paths, spaces, or long Windows paths, prefer an argument file.

Example `ocr-args.txt`:

```text
-i
C:/path/to/document.pdf
-o
C:/path/to/document.md
```

Run:

```powershell
ocr-cli @ocr-args.txt
```

Argument file rules:

- UTF-8 text
- one argument per line
- blank lines are ignored
- lines beginning with `#` are ignored

## Common Parameters

```text
-i, --input <file>          document/image file path; positional input is also supported
-o, --output <file>         output file path; stdout is used when omitted
-m, --model <model>         OCR model; default is PaddleOCR-VL
-f, --format <format>       markdown/text/json; default is markdown
    --output-format <fmt>   API output_format; default is md
    --include-image <bool>  API include_image; default is true
    --include-image-base64 <bool>
                            API include_image_base64; default is true
    --end-pages <n>         API end_pages; default is 0
-p, --prompt <text>         optional prompt
    --poll-interval <sec>   polling interval; default is 5
    --timeout <sec>         timeout; default is 1800
    --api-key <key>         override GITEE_API_KEY
    --base-url <url>        override GITEE API base URL
-h, --help                  show help
```
