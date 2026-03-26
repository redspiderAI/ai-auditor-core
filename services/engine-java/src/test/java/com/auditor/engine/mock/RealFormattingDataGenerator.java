package com.auditor.engine.mock;

import com.auditor.grpc.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class RealFormattingDataGenerator {
    
    public static ParsedData generateRealFormattingData() throws JSONException, IOException {
        // 支持 Windows 和 Linux 路径
        String jsonContent = loadJsonFile();
        JSONObject data = new JSONObject(jsonContent);
        
        ParsedData.Builder builder = ParsedData.newBuilder();
        builder.setDocId("real-thesis");
        
        // 设置元数据
        DocumentMetadata.Builder metadataBuilder = DocumentMetadata.newBuilder();
        metadataBuilder.setTitle("虚拟现实三维全景仿真技术研究");
        metadataBuilder.setPageCount(26);
        builder.setMetadata(metadataBuilder.build());
        
        JSONArray sectionsArray = data.getJSONArray("sections");
        
        for (int i = 0; i < sectionsArray.length(); i++) {
            JSONObject sectionObj = sectionsArray.getJSONObject(i);
            
            Section.Builder sectionBuilder = Section.newBuilder();
            sectionBuilder.setSectionId(sectionObj.getInt("id"));
            sectionBuilder.setText(sectionObj.getString("text"));
            
            String type = sectionObj.getString("type");
            if ("heading".equals(type)) {
                sectionBuilder.setType("heading");
                sectionBuilder.setLevel(sectionObj.getInt("level"));
            } else {
                sectionBuilder.setType("paragraph");
                sectionBuilder.setLevel(0);
            }
            
            JSONObject props = sectionObj.getJSONObject("props");
            sectionBuilder.putProps("font-family", props.getString("font-family"));
            sectionBuilder.putProps("font-size", props.getString("font-size"));
            sectionBuilder.putProps("line-height", props.getString("line-height"));
            sectionBuilder.putProps("color", props.getString("color"));
            sectionBuilder.putProps("bold", String.valueOf(props.getBoolean("bold")));
            
            builder.addSections(sectionBuilder.build());
        }
        
        return builder.build();
    }
    
    /**
     * 加载 JSON 文件，支持 Windows 和 Linux 路径
     */
    private static String loadJsonFile() throws IOException {
        // 尝试多个可能的路径
        String[] possiblePaths = {
            // 相对路径（项目根目录）
            "src/test/resources/real_formatting_data.json",
            // 绝对路径（Linux）
            "/tmp/real_formatting_data.json",
            // 当前工作目录
            "real_formatting_data.json",
            // 用户主目录
            System.getProperty("user.home") + "/real_formatting_data.json"
        };
        
        for (String pathStr : possiblePaths) {
            Path path = Paths.get(pathStr);
            if (Files.exists(path)) {
                System.out.println("✓ 找到测试数据文件: " + path.toAbsolutePath());
                return new String(Files.readAllBytes(path));
            }
        }
        
        // 如果找不到文件，生成默认数据
        System.out.println("⚠ 找不到 real_formatting_data.json，使用默认数据");
        try {
            return generateDefaultJsonData();
        } catch (JSONException e) {
            throw new IOException("生成默认数据失败", e);
        }
    }
    
    /**
     * 生成默认的 JSON 测试数据
     */
    private static String generateDefaultJsonData() throws JSONException {
        JSONObject data = new JSONObject();
        JSONArray sections = new JSONArray();
        
        // 生成 26 个章节（模拟 26 页论文）
        for (int i = 1; i <= 26; i++) {
            JSONObject section = new JSONObject();
            section.put("id", i);
            section.put("text", "这是第 " + i + " 个章节的内容");
            section.put("type", i % 5 == 0 ? "heading" : "paragraph");
            section.put("level", i % 5 == 0 ? 1 : 0);
            
            JSONObject props = new JSONObject();
            props.put("font-family", i % 5 == 0 ? "黑体" : "宋体");
            props.put("font-size", i % 5 == 0 ? "16pt" : "12pt");
            props.put("line-height", "1.83");  // 行距为 1.83
            props.put("color", "black");
            props.put("bold", i % 5 == 0);
            
            section.put("props", props);
            sections.put(section);
        }
        
        data.put("sections", sections);
        return data.toString();
    }
}
