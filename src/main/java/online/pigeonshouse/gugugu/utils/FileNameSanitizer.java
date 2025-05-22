package online.pigeonshouse.gugugu.utils;

import java.util.regex.Pattern;

/**
 * 文件 / 文件夹名称安全化工具。
 */
public final class FileNameSanitizer {
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

    /** 将任意字符串转换为仅含安全字符的文件名。 */
    public static String safe(String name) {
        return UNSAFE.matcher(name).replaceAll("$");
    }
}
