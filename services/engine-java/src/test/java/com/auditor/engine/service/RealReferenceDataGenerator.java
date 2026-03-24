package com.auditor.engine.service;

import com.auditor.grpc.Reference;
import java.util.*;

/**
 * 真实多样化参考文献数据生成器
 * 包含 [J]、[M]、[D]、[C] 四种类型
 * 每条文献包含 1-3 个随机错误
 */
public class RealReferenceDataGenerator {
    
    private static final Random random = new Random(42); // 固定种子保证可重现
    
    // 真实期刊名
    private static final String[] JOURNALS = {
        "中国学术期刊网络出版总库",
        "计算机学报",
        "软件学报",
        "中国科学：信息科学",
        "自动化学报",
        "电子学报",
        "通信学报",
        "信息与控制",
        "系统工程理论与实践",
        "数据库学报",
        "IEEE Transactions on Software Engineering",
        "ACM Computing Surveys",
        "Journal of Machine Learning Research",
        "Nature Machine Intelligence",
        "Science Advances"
    };
    
    // 真实出版社
    private static final String[] PUBLISHERS = {
        "清华大学出版社",
        "机械工业出版社",
        "电子工业出版社",
        "人民邮电出版社",
        "科学出版社",
        "高等教育出版社",
        "中国计算机学会",
        "Springer",
        "ACM Press",
        "IEEE Press"
    };
    
    // 中文作者
    private static final String[] CN_AUTHORS = {
        "张三", "李四", "王五", "赵六", "孙七", "周八", "吴九", "郑十",
        "刘明", "陈浩", "杨洋", "黄金", "何平", "罗军", "高峰", "林涛"
    };
    
    // 英文作者
    private static final String[] EN_AUTHORS = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas"
    };
    
    // 错误类型枚举
    enum ErrorType {
        FULL_COMMA,           // 全角逗号
        FULL_PERIOD,          // 全角句号
        YEAR_EXCEED,          // 年份超出范围
        YEAR_EARLY,           // 年份过早
        YEAR_TWO_DIGIT,       // 年份两位数
        NO_DOT_AFTER_TYPE,    // [J] 后无点号
        NO_AUTHOR,            // 缺少作者
        NO_VOLUME,            // 缺少卷号
        NO_PAGE,              // 缺少页码
        NO_PUBLISHER,         // 缺少出版社
        MULTI_AUTHOR_NO_ET,   // 多作者无"等"
        LOWERCASE_TYPE        // 小写类型标记
    }
    
    /**
     * 生成 200 条真实多样化的参考文献
     */
    public static List<Reference> generateReferences() {
        List<Reference> references = new ArrayList<>();
        
        int journalCount = 50;   // [J] 期刊
        int monoCount = 50;      // [M] 专著
        int thesisCount = 50;    // [D] 学位论文
        int confCount = 50;      // [C] 会议录
        
        int id = 1;
        
        // 生成期刊文献
        for (int i = 0; i < journalCount; i++) {
            references.add(generateJournalReference(id++));
        }
        
        // 生成专著
        for (int i = 0; i < monoCount; i++) {
            references.add(generateMonographReference(id++));
        }
        
        // 生成学位论文
        for (int i = 0; i < thesisCount; i++) {
            references.add(generateThesisReference(id++));
        }
        
        // 生成会议录
        for (int i = 0; i < confCount; i++) {
            references.add(generateConferenceReference(id++));
        }
        
        return references;
    }
    
    /**
     * 生成期刊文献 [J]
     */
    private static Reference generateJournalReference(int id) {
        Set<ErrorType> errors = selectRandomErrors();
        
        String author = generateAuthors(random.nextInt(3) + 1, errors.contains(ErrorType.NO_AUTHOR));
        String title = "论文题名";
        String journal = JOURNALS[random.nextInt(JOURNALS.length)];
        int year = generateYear(errors);
        int volume = random.nextInt(50) + 1;
        int issue = random.nextInt(12) + 1;
        int startPage = random.nextInt(900) + 1;
        int endPage = startPage + random.nextInt(50) + 1;
        
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        sb.append(author).append(". ");
        sb.append(title);
        
        // 处理 [J] 标记
        if (errors.contains(ErrorType.NO_DOT_AFTER_TYPE)) {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[j]");
            } else {
                sb.append("[J]");
            }
        } else {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[j].");
            } else {
                sb.append("[J].");
            }
        }
        
        sb.append(" ");
        sb.append(journal);
        
        // 处理逗号
        if (errors.contains(ErrorType.FULL_COMMA)) {
            sb.append("，");
        } else {
            sb.append(", ");
        }
        
        sb.append(year);
        
        // 处理卷号
        if (!errors.contains(ErrorType.NO_VOLUME)) {
            sb.append(", ").append(volume);
            sb.append("(").append(issue).append(")");
        }
        
        // 处理页码
        if (!errors.contains(ErrorType.NO_PAGE)) {
            sb.append(": ").append(startPage).append("-").append(endPage);
        }
        
        // 处理句号
        if (errors.contains(ErrorType.FULL_PERIOD)) {
            sb.append("。");
        } else {
            sb.append(".");
        }
        
        return Reference.newBuilder()
                .setRefId("[" + id + "]")
                .setRawText(sb.toString())
                .build();
    }
    
    /**
     * 生成专著 [M]
     */
    private static Reference generateMonographReference(int id) {
        Set<ErrorType> errors = selectRandomErrors();
        
        String author = generateAuthors(random.nextInt(3) + 1, errors.contains(ErrorType.NO_AUTHOR));
        String title = "书名";
        String city = "北京";
        String publisher = PUBLISHERS[random.nextInt(PUBLISHERS.length)];
        int year = generateYear(errors);
        
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        sb.append(author).append(". ");
        sb.append(title);
        
        // 处理 [M] 标记
        if (errors.contains(ErrorType.NO_DOT_AFTER_TYPE)) {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[m]");
            } else {
                sb.append("[M]");
            }
        } else {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[m].");
            } else {
                sb.append("[M].");
            }
        }
        
        sb.append(" ");
        
        // 处理出版地和出版社
        if (!errors.contains(ErrorType.NO_PUBLISHER)) {
            sb.append(city).append(": ").append(publisher);
        }
        
        sb.append(", ").append(year);
        
        // 处理句号
        if (errors.contains(ErrorType.FULL_PERIOD)) {
            sb.append("。");
        } else {
            sb.append(".");
        }
        
        return Reference.newBuilder()
                .setRefId("[" + id + "]")
                .setRawText(sb.toString())
                .build();
    }
    
    /**
     * 生成学位论文 [D]
     */
    private static Reference generateThesisReference(int id) {
        Set<ErrorType> errors = selectRandomErrors();
        
        String author = generateAuthors(1, errors.contains(ErrorType.NO_AUTHOR));
        String title = "学位论文题名";
        String degree = "博士学位论文";
        String university = "清华大学";
        int year = generateYear(errors);
        
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        sb.append(author).append(". ");
        sb.append(title);
        
        // 处理 [D] 标记
        if (errors.contains(ErrorType.NO_DOT_AFTER_TYPE)) {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[d]");
            } else {
                sb.append("[D]");
            }
        } else {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[d].");
            } else {
                sb.append("[D].");
            }
        }
        
        sb.append(" ");
        sb.append(degree).append(", ");
        sb.append(university).append(", ");
        sb.append(year);
        
        // 处理句号
        if (errors.contains(ErrorType.FULL_PERIOD)) {
            sb.append("。");
        } else {
            sb.append(".");
        }
        
        return Reference.newBuilder()
                .setRefId("[" + id + "]")
                .setRawText(sb.toString())
                .build();
    }
    
    /**
     * 生成会议录 [C]
     */
    private static Reference generateConferenceReference(int id) {
        Set<ErrorType> errors = selectRandomErrors();
        
        String author = generateAuthors(random.nextInt(3) + 1, errors.contains(ErrorType.NO_AUTHOR));
        String title = "会议论文题名";
        String conference = "第" + (random.nextInt(20) + 1) + "届国际会议";
        String city = "北京";
        int year = generateYear(errors);
        
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        sb.append(author).append(". ");
        sb.append(title);
        
        // 处理 [C] 标记
        if (errors.contains(ErrorType.NO_DOT_AFTER_TYPE)) {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[c]");
            } else {
                sb.append("[C]");
            }
        } else {
            if (errors.contains(ErrorType.LOWERCASE_TYPE)) {
                sb.append("[c].");
            } else {
                sb.append("[C].");
            }
        }
        
        sb.append(" ");
        sb.append(conference);
        
        // 处理逗号
        if (errors.contains(ErrorType.FULL_COMMA)) {
            sb.append("，");
        } else {
            sb.append(", ");
        }
        
        sb.append(city).append(", ");
        sb.append(year);
        
        // 处理句号
        if (errors.contains(ErrorType.FULL_PERIOD)) {
            sb.append("。");
        } else {
            sb.append(".");
        }
        
        return Reference.newBuilder()
                .setRefId("[" + id + "]")
                .setRawText(sb.toString())
                .build();
    }
    
    /**
     * 随机选择 1-3 个错误
     */
    private static Set<ErrorType> selectRandomErrors() {
        Set<ErrorType> errors = new HashSet<>();
        int errorCount = random.nextInt(3) + 1; // 1-3 个错误
        
        ErrorType[] allErrors = ErrorType.values();
        List<ErrorType> errorList = Arrays.asList(allErrors);
        Collections.shuffle(errorList, random);
        
        for (int i = 0; i < Math.min(errorCount, errorList.size()); i++) {
            errors.add(errorList.get(i));
        }
        
        return errors;
    }
    
    /**
     * 生成作者名
     */
    private static String generateAuthors(int count, boolean noAuthor) {
        if (noAuthor) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            
            if (random.nextBoolean()) {
                // 中文作者
                sb.append(CN_AUTHORS[random.nextInt(CN_AUTHORS.length)]);
            } else {
                // 英文作者
                sb.append(EN_AUTHORS[random.nextInt(EN_AUTHORS.length)]);
            }
        }
        
        // 多作者添加"等"
        if (count > 1 && random.nextBoolean()) {
            sb.append("等");
        }
        
        return sb.toString();
    }
    
    /**
     * 生成年份（可能包含错误）
     */
    private static int generateYear(Set<ErrorType> errors) {
        if (errors.contains(ErrorType.YEAR_EXCEED)) {
            return 2027; // 超出范围
        } else if (errors.contains(ErrorType.YEAR_EARLY)) {
            return 1800 + random.nextInt(50); // 过早
        } else if (errors.contains(ErrorType.YEAR_TWO_DIGIT)) {
            return 20 + random.nextInt(10); // 两位数
        } else {
            return 2000 + random.nextInt(26); // 正常年份
        }
    }
    
    /**
     * 生成 builder 代码格式
     */
    public static String generateBuilderCode() {
        List<Reference> references = generateReferences();
        StringBuilder code = new StringBuilder();
        
        code.append("// 生成 200 条真实多样化的参考文献\n");
        code.append("ParsedData.Builder dataBuilder = ParsedData.newBuilder();\n\n");
        
        for (Reference ref : references) {
            code.append("dataBuilder.addReferences(Reference.newBuilder()\n");
            code.append("    .setRefId(\"" + ref.getRefId() + "\")\n");
            code.append("    .setRawText(\"" + escapeString(ref.getRawText()) + "\")\n");
            code.append("    .build());\n\n");
        }
        
        code.append("ParsedData data = dataBuilder.build();\n");
        
        return code.toString();
    }
    
    /**
     * 转义字符串中的特殊字符
     */
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    public static void main(String[] args) {
        List<Reference> references = generateReferences();
        
        System.out.println("生成的参考文献统计:");
        System.out.println("总数: " + references.size());
        System.out.println("期刊 [J]: 50");
        System.out.println("专著 [M]: 50");
        System.out.println("学位论文 [D]: 50");
        System.out.println("会议录 [C]: 50");
        
        // 输出前 5 条示例
        System.out.println("\n前 5 条参考文献示例:");
        for (int i = 0; i < Math.min(5, references.size()); i++) {
            Reference ref = references.get(i);
            System.out.println(ref.getRefId() + " " + ref.getRawText());
        }
    }
}
