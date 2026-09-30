---
name: ai-tools
description: Use when working in the ai-tools repository — a Maven multi-module project that ships two command line tools for Gitee AI, ocr-cli for document and image OCR and asr-cli for audio transcription. Covers module layout, build commands, shared configuration, and which tool skill to load.
---

# ai-tools

A Maven multi-module project that packages Gitee AI capabilities as command line tools.
Both modules wrap the same `java-openai` client, read the same API key, and follow the
same CLI conventions, so build and configuration steps are shared.

## Tool skills

Load the matching skill before using or changing a tool. Each one carries the full
parameter list, output behavior, and known pitfalls for that command.

| Skill | Command | Use it for |
|---|---|---|
| `ocr-cli` | `ocr-cli` | Parse PDF and image documents to markdown, text, or JSON; extract inline page images |
| `asr-cli` | `asr` | Transcribe audio to text, JSON, SRT, or VTT |

Each module `README.md` is the long-form reference and stays authoritative for build
details; the skills summarize what is needed to operate the commands.

| Module | README | Shaded jar |
|---|---|---|
| `ocr-cli` | [`ocr-cli/README.md`](../../../ocr-cli/README.md) | `ocr-cli/target/ocr-cli.jar` |
| `asr-cli` | [`asr-cli/README.md`](../../../asr-cli/README.md) | `asr-cli/target/asr-cli.jar` |

Both modules set the shade plugin `finalName` to `${project.artifactId}`, so the shaded
jar is always `target/<artifactId>.jar`.

## Build

Whole project from the repository root:

```powershell
mvn package -DskipTests
```

A single module:

```powershell
mvn -q -pl ocr-cli -am package -DskipTests
mvn -q -pl asr-cli -am package -DskipTests
```

`-q` hides the Maven log; a build with no output and exit code `0` succeeded.

Windows native executables are optional and need GraalVM plus Visual Studio Build Tools.
The `native` profile reads `graalvm.home`, `msvc.vcvarsall`, and `msvc.cl.exe` from the
module `pom.xml`:

```powershell
mvn -q -pl asr-cli -am -Pnative verify -DskipTests
```

Output is `asr-cli/target/asr.exe`, or `ocr-cli/target/ocr-cli.exe` for the OCR module.
Only the jars exist unless that profile was run.

## Configuration

Both tools call `EnvUtils.load(args)`, which reads the user configuration file
(`secrets.txt` in the user home) and the environment.

```properties
GITEE_API_KEY=your_api_key
GITEE_API_URL=https://ai.gitee.com/v1
```

| Key | Used for |
|---|---|
| `GITEE_API_KEY` | Authorization header on every request; required |
| `GITEE_API_URL` | OpenAI-compatible base URL, used for audio transcription and model listing |
| `GITEE_BASE_URL` | Service root for the async document and task endpoints |

Never print a real key, and never commit `secrets.txt` — it is already in `.gitignore`.

## Conventions shared by both tools

- Output goes to stdout unless `-o <file>` is given; parent directories are created.
  Where a relative `-o` lands is tool-specific: `ocr-cli` resolves it against the input
  file directory, `asr-cli` against the current working directory.
- Common options: `-i/--input`, `-o/--output`, `-m/--model`, `-f/--format`, `--api-key`,
  `--base-url`, `-h/--help`.
- Argument files (`@args.txt`) hold one argument per line, are read as UTF-8, skip blank
  lines, and skip lines starting with `#`. Use them for paths with Chinese characters:
  inline non-ASCII paths are corrupted by the Windows console code page.
- Exit codes: `0` success, `1` invalid arguments or a write failure, `2` the request or
  the remote task failed.
- To read Chinese messages on stderr, start the JVM with UTF-8 output encoding:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
```
