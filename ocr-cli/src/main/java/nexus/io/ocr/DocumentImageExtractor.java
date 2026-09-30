package nexus.io.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把结果中的 {@code data:image/...;base64,...} 内容解码成图片文件，写入指定的图片目录，
 * 并把引用替换为图片路径。
 *
 * <p>接口返回的图片是内联的 data URI，直接写进 Markdown 会让文件体积膨胀且无法预览。
 * 这里落盘为独立图片文件，Markdown 中保留可打开的相对路径。链接按结果文件所在目录
 * 计算，因此结果文件和图片目录不在同一层时链接依然有效。
 */
public class DocumentImageExtractor {

  /** 默认图片目录名。 */
  public static final String DEFAULT_IMAGE_DIR = "document_images";

  /** 内联图片：data:image/jpeg;base64,xxxx。base64 正文不含引号或右括号，遇到即结束。 */
  private static final Pattern DATA_URI_PATTERN = Pattern.compile("data:image/([A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=\\r\\n]+)");

  /** Windows 文件名非法字符。 */
  private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

  private DocumentImageExtractor() {
  }

  /**
   * 抽取内联图片。
   *
   * @param content     结果内容，可以为 null
   * @param imageDir    图片文件写入目录
   * @param linkBaseDir 计算图片相对路径的基准目录，通常是结果文件所在目录
   * @param namePrefix  图片文件名前缀
   * @return 替换后的内容和落盘的图片数量
   */
  public static Result extract(String content, Path imageDir, Path linkBaseDir, String namePrefix) throws IOException {
    if (content == null || content.isEmpty()) {
      return new Result(content, 0);
    }

    String prefix = sanitize(namePrefix);
    String linkPrefix = relativeLinkPrefix(linkBaseDir, imageDir);
    Matcher matcher = DATA_URI_PATTERN.matcher(content);
    StringBuffer buffer = new StringBuffer();
    int count = 0;

    while (matcher.find()) {
      byte[] bytes;
      try {
        bytes = Base64.getMimeDecoder().decode(matcher.group(2));
      } catch (IllegalArgumentException e) {
        // 解码失败时保持原样，避免破坏结果内容
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
        continue;
      }

      count++;
      String filename = prefix + "_" + count + "." + extensionOf(matcher.group(1));
      Files.createDirectories(imageDir);
      Files.write(imageDir.resolve(filename), bytes);
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(linkPrefix + filename));
    }
    matcher.appendTail(buffer);
    return new Result(buffer.toString(), count);
  }

  /**
   * 图片链接按基准目录写成相对路径；跨盘无法相对定位时退回绝对路径。
   */
  private static String relativeLinkPrefix(Path linkBaseDir, Path imageDir) {
    Path base = linkBaseDir.toAbsolutePath().normalize();
    Path target = imageDir.toAbsolutePath().normalize();
    try {
      String relative = base.relativize(target).toString().replace('\\', '/');
      return relative.isEmpty() ? "" : relative + "/";
    } catch (IllegalArgumentException e) {
      return target.toString().replace('\\', '/') + "/";
    }
  }

  private static String extensionOf(String subtype) {
    String lower = subtype.toLowerCase();
    switch (lower) {
    case "jpeg":
    case "jpg":
      return "jpg";
    case "svg+xml":
      return "svg";
    case "x-icon":
      return "ico";
    default:
      return lower.replace("+", "_");
    }
  }

  private static String sanitize(String name) {
    if (name == null) {
      return "image";
    }
    String sanitized = ILLEGAL_FILENAME_CHARS.matcher(name).replaceAll("_").trim();
    return sanitized.isEmpty() ? "image" : sanitized;
  }

  /** 抽取结果：替换后的内容与落盘的图片数量。 */
  public static final class Result {
    private final String content;
    private final int imageCount;

    Result(String content, int imageCount) {
      this.content = content;
      this.imageCount = imageCount;
    }

    public String getContent() {
      return content;
    }

    public int getImageCount() {
      return imageCount;
    }
  }
}
