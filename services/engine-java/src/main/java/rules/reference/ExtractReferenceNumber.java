package rules.reference;

/**
 * 参考文献规则辅助类：提取参考文献列表中的编号
 * 供原始 26 条 reference.drl 规则调用
 */
public class ExtractReferenceNumber {
    
    /**
     * 从引用 ID 中提取数字编号
     * @param refId 引用 ID (如 "[1]")
     * @return 整数编号
     */
    public static int extractReferenceNumber(String refId) {
        if (refId == null || refId.isEmpty()) {
            return 0;
        }
        try {
            // 移除中括号并解析为整数
            String cleaned = refId.replaceAll("\\[|\\]", "").trim();
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}