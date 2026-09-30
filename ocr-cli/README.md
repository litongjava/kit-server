# OCR CLI

`ocr-cli` is a command line tool for Gitee document OCR/parse tasks.

Agent skill: [`.agents/skills/ocr-cli/SKILL.md`](../.agents/skills/ocr-cli/SKILL.md).

Default model:

```text
PaddleOCR-VL
```

`PaddleOCR-VL` is the public model name. Because the platform has retired that
entry, the CLI routes it to `PaddleOCR-VL-1.5` internally. Any other value passed
with `-m` is sent unchanged.

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

## Build

```powershell
mvn -q -pl ocr-cli -am package -DskipTests
```

Output:

```text
ocr-cli/target/ocr-cli.jar
```

## Usage

```powershell
java -jar ocr-cli/target/ocr-cli.jar -i "C:/path/to/document.pdf" -o "C:/path/to/document.md"
```

A relative `-o` lands next to the input file, which is usually what you want:

```powershell
java -jar ocr-cli/target/ocr-cli.jar -i "C:/path/to/document.pdf" -o document.md
```

That writes `C:/path/to/document.md` plus `C:/path/to/document_images/`.

Use raw JSON output:

```powershell
java -jar ocr-cli/target/ocr-cli.jar --format json -i "C:/path/to/image.png" -o "C:/path/to/result.json"
```

Override API request fields explicitly:

```powershell
java -jar ocr-cli/target/ocr-cli.jar -i "C:/path/to/document.pdf" --output-format md --include-image true --include-image-base64 true --end-pages 0
```

For Windows paths containing Chinese characters, prefer an argument file:

```text
-i
C:/path/to/document.pdf
-o
C:/path/to/document.md
```

Run:

```powershell
java -jar ocr-cli/target/ocr-cli.jar @ocr-args.txt
```

Chinese paths break when they are passed inline on a Windows console, because the
console code page is not UTF-8. The argument file is read as UTF-8, so use it for
any path that contains non-ASCII characters. To read Chinese messages on stderr,
start the JVM with UTF-8 output encoding:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
```

## Output location

Output is anchored to the directory that holds the input file:

| Argument | Result file |
|---|---|
| `-o report.md` | `<input-dir>/report.md` |
| `-o "C:/other/report.md"` | exactly `C:/other/report.md` |
| no `-o` | markdown goes to stdout |

Inline images always go to `<input-dir>/document_images`, whatever `-o` says.

## Inline images

The service returns page images as inline `data:image/...;base64,...` values. For
`markdown` and `text` output, the CLI decodes them into separate files and replaces
the inline value with the file path:

```text
<input-dir>/document_images/<output-name>_1.jpg
<input-dir>/document_images/<output-name>_2.jpg
```

```html
<img src="document_images/document_1.jpg" alt="Image" width="20%" />
```

Rules:

- Images are written to `document_images` in the input file directory, so the result
  and its images stay together next to the source document.
- The link is written relative to the result file. `-o report.md` produces
  `document_images/report_1.jpg`; an absolute `-o` outside the input directory produces
  something like `../documents/document_images/report_1.jpg`, so the images stay
  reachable from wherever the result file sits.
- The file name prefix is the result file base name, so several documents can share
  one directory. `-o report.md` produces `report_1.jpg`, `report_2.jpg`, and so on.
- The extension comes from the media type in the data URI (`jpeg` becomes `.jpg`).
- `--include-image-base64 false` stops the service from sending inline data, so there
  is nothing to decode and the reference keeps whatever the service returns.
- `json` output keeps the raw response, including any inline base64 data.
- The command prints `Extracted N image(s) to: <path>` when images were written.

## Exit Codes

```text
0  success
1  invalid arguments, or the result file could not be written
2  the OCR request or the remote task failed
```
