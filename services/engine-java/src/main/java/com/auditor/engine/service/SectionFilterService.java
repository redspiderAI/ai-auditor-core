package com.auditor.engine.service;

import com.auditor.grpc.ParsedData;
import com.auditor.grpc.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Section 预过滤服务
 *
 * <p>在将 {@link ParsedData} 送入各规则引擎之前，对 sections 列表进行预处理：
 * <ol>
 *   <li><b>停止检测（截断）</b>：遍历 sections 时，一旦遇到 <b>type=heading</b> 且文本包含
 *       {@link #STOP_KEYWORDS} 中任意关键词的 section，立即截断——该 section
 *       及其后续所有 section 均不参与后续审查。
 *       注意：type=paragraph 的目录条目（如「学位论文数据集\t25」）不触发截断，避免误截断正文。</li>
 *   <li><b>白名单（跳过）</b>：{@link #WHITELIST_SECTION_TEXTS} 中列出的 section
 *       文本即使出现在截断点之前，也会被从审查列表中移除，不产生任何 Issue。</li>
 * </ol>
 *
 * <p>典型场景：李良循毕业论文第 33 页的「学位论文数据集」章节是附录性元数据表格，
 * 不属于论文正文，不应被格式/完整性/参考文献规则检查。
 */
@Service
public class SectionFilterService {

    private static final Logger logger = LoggerFactory.getLogger(SectionFilterService.class);

    /**
     * 停止检测关键词列表。
     * 当某个 <b>type=heading</b> 的 section 文本包含以下任意关键词时，
     * 该 section 及其后所有 section 均不进入规则引擎。
     * type=paragraph 的目录条目即使包含相同文字也不触发截断。
     */
    public static final List<String> STOP_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "学位论文数据集"
    ));

    /**
     * 白名单 section 文本列表（精确匹配，忽略首尾空白）。
     * 白名单中的 section 即使出现在截断点之前，也会被静默跳过，不送入规则引擎。
     */
    public static final List<String> WHITELIST_SECTION_TEXTS = Collections.unmodifiableList(Arrays.asList(
            "学位论文数据集",
            "独创性声明",
            "学位论文版权使用授权书",
            "版权声明"
    ));

    /**
     * 对 {@link ParsedData} 进行 section 过滤，返回一个新的 {@link ParsedData}，
     * 其 sections 列表已按照停止检测和白名单规则截断/过滤。
     * 原始 {@code data} 对象不会被修改（Protobuf 对象本身是不可变的）。
     *
     * @param data 原始解析数据
     * @return 过滤后的 ParsedData；若 {@code data} 为 null，则原样返回 null
     */
    public ParsedData filterSections(ParsedData data) {
        if (data == null) {
            return null;
        }

        List<Section> originalSections = data.getSectionsList();
        if (originalSections.isEmpty()) {
            return data;
        }

        List<Section> filteredSections = new ArrayList<>();
        int stopIndex = -1;

        for (int i = 0; i < originalSections.size(); i++) {
            Section section = originalSections.get(i);
            String text = section.getText() == null ? "" : section.getText().trim();
            String type = section.getType() == null ? "" : section.getType();

            // 1. 检查是否触发停止关键词（优先级高于白名单）
            //    关键约束：只有 type=heading 的章节标题才触发截断。
            //    type=paragraph 的目录条目（如「学位论文数据集\t25」）文本虽然包含关键词，
            //    但它是目录页的普通段落，不是真正的章节标题，不应触发截断。
            if ("heading".equals(type) && matchesStopKeyword(text)) {
                stopIndex = i;
                logger.info("检测到停止关键词，section[{}] type=heading text='{}' — 该 section 及后续 {} 个 section 将被跳过",
                        i, text, originalSections.size() - i);
                break;
            }

            // 2. 检查是否在白名单中（白名单 section 静默跳过，不加入审查列表）
            if (isWhitelisted(text)) {
                logger.debug("白名单命中，跳过 section[{}] text='{}'", i, text);
                continue;
            }

            filteredSections.add(section);
        }

        // 如果没有任何截断且没有白名单命中，直接返回原始数据
        if (stopIndex == -1 && filteredSections.size() == originalSections.size()) {
            logger.debug("section 过滤：无截断，无白名单命中，返回原始数据");
            return data;
        }

        int removedCount = originalSections.size() - filteredSections.size();
        logger.info("section 过滤完成：原始 {} 个，过滤后 {} 个，移除 {} 个",
                originalSections.size(), filteredSections.size(), removedCount);

        // 构造新的 ParsedData，仅替换 sections 列表
        ParsedData.Builder builder = data.toBuilder();
        builder.clearSections();
        builder.addAllSections(filteredSections);
        return builder.build();
    }

    /**
     * 判断给定文本是否包含任意停止关键词。
     */
    public boolean matchesStopKeyword(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String keyword : STOP_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断给定文本是否在白名单中（精确匹配，忽略首尾空白）。
     */
    public boolean isWhitelisted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String whitelisted : WHITELIST_SECTION_TEXTS) {
            if (text.equals(whitelisted.trim())) {
                return true;
            }
        }
        return false;
    }
}
