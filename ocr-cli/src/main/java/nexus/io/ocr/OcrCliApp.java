package nexus.io.ocr;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nexus.io.gitee.GiteeClient;
import nexus.io.gitee.GiteeConst;
import nexus.io.gitee.GiteeDocumentParseRequest;
import nexus.io.gitee.GiteeDocumentSegment;
import nexus.io.gitee.GiteeDocumentOutput;
import nexus.io.gitee.GiteeModels;
import nexus.io.gitee.GiteeSimpleMarkdownUtils;
import nexus.io.gitee.GiteeTaskResponse;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;

public class OcrCliApp {

  private static final String DEFAULT_MODEL = GiteeModels.PADDLEOCR_VL;
  private static final String DEFAULT_FORMAT = "markdown";

  /**
   * 模型别名：对外仍可使用稳定模型名，内部自动定向到当前可用的模型版本。 平台侧停用或改名时，只需要调整这里的映射。
   */
  private static final Map<String, String> MODEL_ALIASES = Map.of(GiteeModels.PADDLEOCR_VL, GiteeModels.PADDLEOCR_VL_1_5);

  public static void main(String[] args) {
    int exitCode = run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args) {
    try {
      CliOptions options = CliOptions.parse(args);
      if (options.help) {
        printUsage();
        return 0;
      }

      EnvUtils.load(args);
      applyEnvOverrides(options);
      validate(options);

      GiteeTaskResponse task = parseAndWait(options);
      String body = render(task, options.format);

      Path inputDir = resolveInputDir(options);
      Path outputPath = options.outputFile == null ? null : resolveOutputPath(options);

      if (isImageExtractionFormat(options.format)) {
        Path imageDir = inputDir.resolve(DocumentImageExtractor.DEFAULT_IMAGE_DIR);
        Path linkBaseDir = outputPath == null ? inputDir : parentOf(outputPath, inputDir);
        DocumentImageExtractor.Result extracted = DocumentImageExtractor.extract(body, imageDir, linkBaseDir,
            resolveImagePrefix(options));
        body = extracted.getContent();
        if (extracted.getImageCount() > 0) {
          System.out.println("Extracted " + extracted.getImageCount() + " image(s) to: " + imageDir);
        }
      }

      if (outputPath == null) {
        System.out.println(body);
      } else {
        Files.createDirectories(parentOf(outputPath, inputDir));
        Files.writeString(outputPath, body, StandardCharsets.UTF_8);
        System.out.println("OCR result saved to: " + outputPath);
      }
      return 0;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.err.println();
      printUsage();
      return 1;
    } catch (IOException e) {
      System.err.println("Write output failed: " + e.getMessage());
      return 1;
    } catch (Exception e) {
      System.err.println("OCR failed: " + e.getMessage());
      return 2;
    }
  }

  /**
   * 解析实际发送给接口的模型名。对外保持稳定模型名，内部自动定向到当前可用版本。
   */
  private static String resolveModel(String model) {
    if (model == null) {
      return null;
    }
    String target = MODEL_ALIASES.get(model.trim());
    return target != null ? target : model;
  }

  /** markdown 和 text 会把内联图片落盘；json 保留原始响应，便于排查。 */
  private static boolean isImageExtractionFormat(String format) {
    return "markdown".equalsIgnoreCase(format) || "text".equalsIgnoreCase(format);
  }

  /** 输入文件所在目录，所有输出都相对它定位；输入是裸文件名时就是当前工作目录。 */
  private static Path resolveInputDir(CliOptions options) {
    return parentOf(options.inputFile.toPath().toAbsolutePath(), Path.of("").toAbsolutePath());
  }

  /**
   * 结果文件路径。相对路径按输入文件所在目录解析，绝对路径原样使用。
   */
  private static Path resolveOutputPath(CliOptions options) {
    Path outputPath = options.outputFile.toPath();
    if (outputPath.isAbsolute()) {
      return outputPath.normalize();
    }
    return resolveInputDir(options).resolve(outputPath).normalize();
  }

  private static Path parentOf(Path path, Path fallback) {
    Path parent = path.toAbsolutePath().normalize().getParent();
    return parent != null ? parent : fallback;
  }

  /** 图片文件名前缀，取结果文件主文件名；没有输出文件时取输入文件主文件名。 */
  private static String resolveImagePrefix(CliOptions options) {
    if (options.outputFile != null) {
      return stripExtension(options.outputFile.getName());
    }
    return stripExtension(options.inputFile.getName());
  }

  private static String stripExtension(String filename) {
    int index = filename.lastIndexOf('.');
    return index > 0 ? filename.substring(0, index) : filename;
  }

  private static GiteeTaskResponse parseAndWait(CliOptions options) throws InterruptedException {
    GiteeClient client = new GiteeClient();
    GiteeDocumentParseRequest request = new GiteeDocumentParseRequest();
    request.setModel(resolveModel(options.model));
    request.setInclude_image(options.includeImage);
    request.setInclude_image_base64(options.includeImageBase64);
    request.setEnd_pages(options.endPages);
    request.setOutput_format(options.outputFormat);
    request.setPrompt(options.prompt);

    GiteeTaskResponse current = client.parseDocument(options.inputFile, request);
    long deadline = System.currentTimeMillis() + options.timeoutSeconds * 1000L;

    while (System.currentTimeMillis() < deadline) {
      String status = current.getStatus();
      if (isTerminalSuccess(status)) {
        return current;
      }
      if (isTerminalFailure(status)) {
        throw new IllegalStateException("Document parse failed, status: " + status);
      }
      if (current.getTask_id() == null) {
        throw new IllegalStateException("Document parse did not return task_id.");
      }
      Thread.sleep(options.pollIntervalSeconds * 1000L);
      current = client.getTask(current.getTask_id());
    }
    throw new IllegalStateException("Document parse timed out, task_id: " + current.getTask_id());
  }

  private static String render(GiteeTaskResponse task, String format) {
    if ("json".equalsIgnoreCase(format)) {
      return JsonUtils.toJson(task);
    }

    GiteeDocumentOutput output = task.getOutput();
    if (output == null) {
      return "";
    }
    if ("text".equalsIgnoreCase(format)) {
      String text = output.getText_result();
      if (text != null) {
        return text;
      }
      text = output.getText();
      if (text != null) {
        return text;
      }
      return renderSegments(output);
    }
    return GiteeSimpleMarkdownUtils.toMarkdown(output);
  }

  private static String renderSegments(GiteeDocumentOutput output) {
    if (output.getSegments() == null || output.getSegments().isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (GiteeDocumentSegment segment : output.getSegments()) {
      if (segment != null && segment.getContent() != null && !segment.getContent().trim().isEmpty()) {
        if (sb.length() > 0) {
          sb.append("\n\n");
        }
        sb.append(segment.getContent().trim());
      }
    }
    return sb.toString();
  }

  private static boolean isTerminalSuccess(String status) {
    return "success".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)
        || "finished".equalsIgnoreCase(status);
  }

  private static boolean isTerminalFailure(String status) {
    return "failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
        || "canceled".equalsIgnoreCase(status);
  }

  private static void applyEnvOverrides(CliOptions options) {
    if (options.apiKey != null) {
      EnvUtils.set(GiteeConst.GITEE_API_KEY, options.apiKey);
    }
    if (options.baseUrl != null) {
      EnvUtils.set(GiteeConst.GITEE_API_URL_KEY, options.baseUrl);
      EnvUtils.set("GITEE_BASE_URL", options.baseUrl.replaceFirst("/v1$", ""));
    }
  }

  private static void validate(CliOptions options) {
    if (options.inputFile == null) {
      throw new IllegalArgumentException("Missing input file. Use -i <file> or pass the file path as the first argument.");
    }
    if (!options.inputFile.isFile()) {
      throw new IllegalArgumentException("Input file does not exist or is not a file: " + options.inputFile);
    }
    if (!"markdown".equalsIgnoreCase(options.format) && !"text".equalsIgnoreCase(options.format)
        && !"json".equalsIgnoreCase(options.format)) {
      throw new IllegalArgumentException("Unsupported format: " + options.format);
    }
  }

  private static void printUsage() {
    System.out.println("""
        Usage:
          ocr-cli [options] <document-file>
          ocr-cli @args.txt

        Options:
          -i, --input <file>          Document/image file to parse. Positional file path is also supported.
          -o, --output <file>         Save OCR result to a file. Prints to stdout when omitted.
          -m, --model <model>         OCR model. Default: PaddleOCR-VL.
          -f, --format <format>       markdown, text, or json. Default: markdown.
              --output-format <fmt>   API output_format. Default: md.
              --include-image <bool>  API include_image. Default: true.
              --include-image-base64 <bool>
                                      API include_image_base64. Default: true.
              --end-pages <n>         API end_pages. Default: 0.
          -p, --prompt <text>         Optional prompt.
              --poll-interval <sec>   Polling interval. Default: 5.
              --timeout <sec>         Timeout. Default: 1800.
              --api-key <key>         Override GITEE_API_KEY from environment/config.
              --base-url <url>        Override GITEE API base URL.
          -h, --help                  Show this help.

        Notes:
          PaddleOCR-VL is the public model name and is routed to PaddleOCR-VL-1.5 internally.
          Output is anchored to the input file directory: a relative -o path resolves against it,
          and inline base64 images go to its document_images directory. Image references are
          replaced with paths relative to the result file.

        Examples:
          ocr-cli -i document.pdf -o document.md
          ocr-cli --model PaddleOCR-VL --format json image.png
          ocr-cli @ocr-args.txt

        Maven jar:
          java -jar ocr-cli.jar [options] <document-file>
        """);
  }

  private static class CliOptions {
    private File inputFile;
    private File outputFile;
    private String model = DEFAULT_MODEL;
    private String format = DEFAULT_FORMAT;
    private String outputFormat = "md";
    private Boolean includeImage = Boolean.TRUE;
    private Boolean includeImageBase64 = Boolean.TRUE;
    private Integer endPages = Integer.valueOf(0);
    private String prompt;
    private int pollIntervalSeconds = 5;
    private int timeoutSeconds = 1800;
    private String apiKey;
    private String baseUrl;
    private boolean help;

    private static CliOptions parse(String[] args) {
      args = expandArgumentFiles(args);
      CliOptions options = new CliOptions();
      List<String> positional = new ArrayList<>();

      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
        case "-h":
        case "--help":
          options.help = true;
          break;
        case "-i":
        case "--input":
          options.inputFile = new File(requireValue(args, ++i, arg));
          break;
        case "-o":
        case "--output":
          options.outputFile = new File(requireValue(args, ++i, arg));
          break;
        case "-m":
        case "--model":
          options.model = requireValue(args, ++i, arg);
          break;
        case "-f":
        case "--format":
          options.format = requireValue(args, ++i, arg);
          break;
        case "--output-format":
          options.outputFormat = requireValue(args, ++i, arg);
          break;
        case "--include-image":
          options.includeImage = parseBoolean(requireValue(args, ++i, arg), arg);
          break;
        case "--include-image-base64":
          options.includeImageBase64 = parseBoolean(requireValue(args, ++i, arg), arg);
          break;
        case "--end-pages":
          options.endPages = parseNonNegativeInt(requireValue(args, ++i, arg), arg);
          break;
        case "-p":
        case "--prompt":
          options.prompt = requireValue(args, ++i, arg);
          break;
        case "--poll-interval":
          options.pollIntervalSeconds = parsePositiveInt(requireValue(args, ++i, arg), arg);
          break;
        case "--timeout":
          options.timeoutSeconds = parsePositiveInt(requireValue(args, ++i, arg), arg);
          break;
        case "--api-key":
          options.apiKey = requireValue(args, ++i, arg);
          break;
        case "--base-url":
          options.baseUrl = requireValue(args, ++i, arg);
          break;
        default:
          if (arg.startsWith("-")) {
            throw new IllegalArgumentException("Unknown option: " + arg);
          }
          positional.add(arg);
          break;
        }
      }

      if (options.inputFile == null && !positional.isEmpty()) {
        options.inputFile = new File(positional.remove(0));
      }
      if (!positional.isEmpty()) {
        throw new IllegalArgumentException("Unexpected argument: " + positional.get(0));
      }
      return options;
    }

    private static String[] expandArgumentFiles(String[] args) {
      List<String> expanded = new ArrayList<>();
      for (String arg : args) {
        if (arg.startsWith("@") && arg.length() > 1) {
          Path path = Path.of(arg.substring(1));
          try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
              String value = stripBom(line).strip();
              if (!value.isEmpty() && !value.startsWith("#")) {
                expanded.add(value);
              }
            }
          } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read argument file: " + path);
          }
        } else {
          expanded.add(arg);
        }
      }
      return expanded.toArray(String[]::new);
    }

    private static String stripBom(String value) {
      if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
        return value.substring(1);
      }
      return value;
    }

    private static String requireValue(String[] args, int index, String optionName) {
      if (index >= args.length || args[index].startsWith("-")) {
        throw new IllegalArgumentException("Missing value for " + optionName);
      }
      return args[index];
    }

    private static int parsePositiveInt(String value, String optionName) {
      try {
        int result = Integer.parseInt(value);
        if (result > 0) {
          return result;
        }
      } catch (NumberFormatException ignored) {
      }
      throw new IllegalArgumentException("Invalid positive integer for " + optionName + ": " + value);
    }

    private static int parseNonNegativeInt(String value, String optionName) {
      try {
        int result = Integer.parseInt(value);
        if (result >= 0) {
          return result;
        }
      } catch (NumberFormatException ignored) {
      }
      throw new IllegalArgumentException("Invalid non-negative integer for " + optionName + ": " + value);
    }

    private static Boolean parseBoolean(String value, String optionName) {
      if ("true".equalsIgnoreCase(value)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(value)) {
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Invalid boolean for " + optionName + ": " + value);
    }
  }
}
