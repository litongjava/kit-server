package nexus.io.asr;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSON;

import nexus.io.gitee.GiteeClient;
import nexus.io.gitee.GiteeConst;
import nexus.io.gitee.GiteeDocumentOutput;
import nexus.io.gitee.GiteeModels;
import nexus.io.gitee.GiteeTaskResponse;
import nexus.io.model.http.response.ResponseVo;
import nexus.io.openai.whisper.WhisperResponseFormat;
import nexus.io.openai.whisper.WhisperTranscriptionsRequest;
import nexus.io.tio.utils.environment.EnvUtils;

public class AsrCliApp {

  private static final String DEFAULT_MODEL = GiteeModels.WHISPER_LARGE_V3;
  private static final String DEFAULT_RESPONSE_FORMAT = WhisperResponseFormat.text;

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
      String body;
      if (Boolean.TRUE.equals(options.async)) {
        body = transcribeAsync(options);
      } else {
        ResponseVo response = transcribe(options);
        body = response.getBodyString();

        if (!response.isOk()) {
          System.err.println("Transcription failed, status code: " + response.getCode());
          System.err.println(body);
          return 2;
        }
        body = extractTranscriptionText(body);
      }

      if (options.outputFile == null) {
        System.out.println(body);
      } else {
        Path outputPath = options.outputFile.toPath();
        Path parent = outputPath.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(outputPath, body, StandardCharsets.UTF_8);
        System.out.println("Transcription saved to: " + outputPath.toAbsolutePath());
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
      System.err.println("Transcription failed: " + e.getMessage());
      return 2;
    }
  }

  private static ResponseVo transcribe(CliOptions options) {
    WhisperTranscriptionsRequest request = new WhisperTranscriptionsRequest();
    request.setFile(options.inputFile);
    request.setModel(options.model);
    request.setResponse_format(options.responseFormat);
    request.setLanguage(options.language);
    request.setPrompt(options.prompt);
    request.setTemperature(options.temperature);
    request.setStream(options.stream);
    return GiteeClient.transcriptions(options.inputFile, request);
  }

  private static String extractTranscriptionText(String body) {
    return JSON.parseObject(body).getString("text");
  }

  private static String transcribeAsync(CliOptions options) throws InterruptedException {
    WhisperTranscriptionsRequest request = new WhisperTranscriptionsRequest();
    request.setFile(options.inputFile);
    request.setModel(options.model);
    request.setResponse_format(options.responseFormat);
    request.setLanguage(options.language);
    request.setPrompt(options.prompt);
    request.setTemperature(options.temperature);
    request.setStream(options.stream);

    GiteeTaskResponse current = GiteeClient.asyncAudioTranscriptions(options.inputFile, request);
    GiteeClient client = new GiteeClient(EnvUtils.get(GiteeConst.GITEE_API_KEY),
        EnvUtils.get(GiteeConst.GITEE_API_URL_KEY, GiteeConst.API_PREFIX_URL).replaceFirst("/v1$", ""));
    long deadline = System.currentTimeMillis() + options.asyncTimeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      String status = current.getStatus();
      if (isTerminalSuccess(status)) {
        return extractAsyncText(current);
      }
      if (isTerminalFailure(status)) {
        throw new IllegalStateException("Async transcription failed, status: " + status);
      }
      if (current.getTask_id() == null) {
        throw new IllegalStateException("Async transcription did not return task_id.");
      }
      Thread.sleep(options.pollIntervalSeconds * 1000L);
      current = client.getTask(current.getTask_id());
    }
    throw new IllegalStateException("Async transcription timed out, task_id: " + current.getTask_id());
  }

  private static String extractAsyncText(GiteeTaskResponse task) {
    GiteeDocumentOutput output = task.getOutput();
    if (output != null) {
      if (output.getText() != null) {
        return output.getText();
      }
      if (output.getContent() != null) {
        return output.getContent();
      }
      if (output.getText_result() != null) {
        return output.getText_result();
      }
    }
    return "";
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
    }
  }

  private static void validate(CliOptions options) {
    if (options.inputFile == null) {
      throw new IllegalArgumentException("Missing input file. Use -i <mp3-file> or pass the file path as the first argument.");
    }
    if (!options.inputFile.isFile()) {
      throw new IllegalArgumentException("Input file does not exist or is not a file: " + options.inputFile);
    }
    if (!isSupportedResponseFormat(options.responseFormat)) {
      throw new IllegalArgumentException("Unsupported response format: " + options.responseFormat);
    }
  }

  private static boolean isSupportedResponseFormat(String responseFormat) {
    return WhisperResponseFormat.text.equals(responseFormat) || WhisperResponseFormat.json.equals(responseFormat)
        || WhisperResponseFormat.verbose_json.equals(responseFormat) || WhisperResponseFormat.srt.equals(responseFormat)
        || WhisperResponseFormat.vtt.equals(responseFormat);
  }

  private static void printUsage() {
    System.out.println("""
        Usage:
          asr [options] <audio-file>
          asr @args.txt

        Options:
          -i, --input <file>          Audio file to transcribe. Positional file path is also supported.
          -o, --output <file>         Save transcription to a file. Prints to stdout when omitted.
          -m, --model <model>         ASR model. Default: whisper-large-v3.
          -f, --format <format>       text, json, verbose_json, srt, or vtt. Default: text.
          -l, --language <lang>       Optional language code, e.g. zh.
          -p, --prompt <text>         Optional prompt to guide transcription.
          -t, --temperature <number>  Optional sampling temperature.
              --stream <true|false>   Optional stream flag passed to the API.
              --async <true|false>    Use Gitee async audio transcription endpoint.
              --poll-interval <sec>   Async polling interval. Default: 5.
              --async-timeout <sec>   Async timeout. Default: 1800.
              --api-key <key>         Override GITEE_API_KEY from environment/config.
              --base-url <url>        Override GITEE_API_URL from environment/config.
          -h, --help                  Show this help.

        Examples:
          asr -i audio.mp3 -o audio.txt
          asr --model whisper-large-v3-turbo --format srt audio.mp3
          asr @asr-args.txt

        Maven jar:
          java -jar asr-cli.jar [options] <audio-file>
        """);
  }

  private static class CliOptions {
    private File inputFile;
    private File outputFile;
    private String model = DEFAULT_MODEL;
    private String responseFormat = DEFAULT_RESPONSE_FORMAT;
    private String language;
    private String prompt;
    private Float temperature;
    private Boolean stream;
    private Boolean async;
    private int pollIntervalSeconds = 5;
    private int asyncTimeoutSeconds = 1800;
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
        case "--response-format":
          options.responseFormat = requireValue(args, ++i, arg);
          break;
        case "-l":
        case "--language":
          options.language = requireValue(args, ++i, arg);
          break;
        case "-p":
        case "--prompt":
          options.prompt = requireValue(args, ++i, arg);
          break;
        case "-t":
        case "--temperature":
          options.temperature = parseFloat(requireValue(args, ++i, arg), arg);
          break;
        case "--stream":
          options.stream = parseBoolean(requireValue(args, ++i, arg), arg);
          break;
        case "--async":
          options.async = parseBoolean(requireValue(args, ++i, arg), arg);
          break;
        case "--poll-interval":
          options.pollIntervalSeconds = parsePositiveInt(requireValue(args, ++i, arg), arg);
          break;
        case "--async-timeout":
          options.asyncTimeoutSeconds = parsePositiveInt(requireValue(args, ++i, arg), arg);
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

    private static Float parseFloat(String value, String optionName) {
      try {
        return Float.parseFloat(value);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid number for " + optionName + ": " + value);
      }
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
  }
}
