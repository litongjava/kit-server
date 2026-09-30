---
name: ocr-cli
description: Use when parsing a PDF or image with ocr-cli, or when changing that tool — covers the ocr-cli command, its model routing, output formats, the document_images extraction, exit codes, and Windows path pitfalls.
---

# OCR CLI

`ocr-cli` sends a document or image to Gitee document processing and writes the OCR
result to stdout or an output file.

Module README: [`ocr-cli/README.md`](../../../ocr-cli/README.md).
Repository-wide build and configuration: load the `ai-tools` skill.

## Model

Default model:

```text
PaddleOCR-VL
```

`PaddleOCR-VL` is the public model name. The CLI routes it to `PaddleOCR-VL-1.5`
internally, because the plain `PaddleOCR-VL` entry was retired by the platform. Any
other `-m` value is sent unchanged, so `--model PaddleOCR-VL` and no `-m` at all behave
the same.

## Output formats

```text
markdown
text
json
```

`markdown` and `text` have inline images extracted to files. `json` keeps the raw
response, including base64 data, for troubleshooting.

API request fields mirrored from the Python example:

```text
model=PaddleOCR-VL
include_image=true
include_image_base64=true
end_pages=0
output_format=md
```

## Usage

Show help:

```powershell
java -jar ocr-cli/target/ocr-cli.jar --help
```

Parse to Markdown:

```powershell
java -jar ocr-cli/target/ocr-cli.jar -i "C:/path/to/document.pdf" -o "C:/path/to/document.md"
```

Raw JSON output:

```powershell
java -jar ocr-cli/target/ocr-cli.jar --format json -i "C:/path/to/image.png" -o "C:/path/to/result.json"
```

Override API request fields explicitly:

```powershell
java -jar ocr-cli/target/ocr-cli.jar -i "C:/path/to/document.pdf" --output-format md --include-image true --include-image-base64 true --end-pages 0
```

## Output location

Output is anchored to the input file directory:

| Argument | Result file |
|---|---|
| `-o report.md` | `<input-dir>/report.md` |
| `-o "C:/other/report.md"` | exactly `C:/other/report.md` |
| no `-o` | markdown goes to stdout |

Inline images always go to `<input-dir>/document_images`, whatever `-o` says. Prefer a
relative `-o` so the result and its images land beside the source document.

## Inline images

For `markdown` and `text`, the CLI decodes inline `data:image/...;base64,...` values
into files and replaces the value with the file path:

```text
<input-dir>/document_images/<output-name>_1.jpg
```

```html
<img src="document_images/document_1.jpg" alt="Image" width="20%" />
```

- `document_images` is created in the input file directory.
- The link is written relative to the result file, so an absolute `-o` outside the input
  directory yields a path such as `../documents/document_images/report_1.jpg` and the
  images stay reachable.
- The prefix is the result file base name, so several documents can share one directory.
- The extension comes from the media type in the data URI (`jpeg` becomes `.jpg`).
- `--include-image-base64 false` leaves no inline data to decode.
- The command prints `Extracted N image(s) to: <path>` when images were written.

## Argument files

For Chinese paths, spaces, or long Windows paths, prefer an argument file.

Example `ocr-args.txt`:

```text
-i
C:/path/to/document.pdf
-o
C:/path/to/document.md
```

```powershell
java -jar ocr-cli/target/ocr-cli.jar @ocr-args.txt
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
    --base-url <url>        override the Gitee service root
-h, --help                  show help
```

## Exit codes

```text
0  success
1  invalid arguments, or the result file could not be written
2  the OCR request or the remote task failed
```

## Failure notes

- `该模型已停用` means the model name is retired; the alias table in `OcrCliApp`
  (`MODEL_ALIASES`) is where a replacement is registered.
- A `400` with a Chinese message is usually a rejected model name or an unsupported file.
- Parsing is asynchronous: the CLI submits a task and polls until `--timeout` seconds
  elapse. A large PDF can take minutes, so raise `--timeout` instead of retrying blindly.
