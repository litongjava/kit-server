# OCR CLI

`ocr-cli` is a command line tool for Gitee document OCR/parse tasks.

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
