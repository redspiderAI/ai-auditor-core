package rules.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参考文献规则辅助类：提取正文中的引用编号
 * 供原始 26 条 reference.drl 规则调用
 */
public class ExtractCitationNumbers {
    // 支持 [1], [1,2], [1-3] 等格式
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\s*(\\d+(?:\\s*[-,\\s]\\s*\\d+)*)\\s*\\]");

    /**
     * 提取引用编号
     * @param text 输入文本
     * @return 编号列表
     */
    public static List<Integer> extractCitationNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return numbers;
        }
        Matcher matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            String group = matcher.group(1);
            String[] parts = group.split("[,\\s]+");
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end; i++) numbers.add(i);
                    } catch (Exception ignored) {}
                } else {
                    try {
                        numbers.add(Integer.parseInt(part.trim()));
                    } catch (Exception ignored) {}
                }
            }
        }
        return numbers;
    }
}