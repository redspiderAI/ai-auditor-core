import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Dict, Optional, Any

class ElementType:
    class Heading:
        def __init__(self, level: int):
            self.level = level
    
    Paragraph = "Paragraph"
    Table = "Table"
    Equation = "Equation"

class DocumentSection:
    def __init__(self, id: int, element_type, raw_text: str, formatting: Dict[str, str], xml_path: str, offset: int):
        self.id = id
        self.element_type = element_type
        self.raw_text = raw_text
        self.formatting = formatting
        self.xml_path = xml_path
        self.offset = offset  # 添加偏移量

class DocxParser:
    W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    STYLES_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    
    def __init__(self):
        self.styles = {}
        self.style_inheritance = {}
    
    def parse(self, path: Path) -> Dict[str, Any]:
        with zipfile.ZipFile(path, 'r') as archive:
            # 解析样式
            self._parse_styles(archive)
            
            # 读取 document.xml
            with archive.open('word/document.xml') as f:
                doc_xml = f.read().decode('utf-8')
            
            # 解析 XML
            root = ET.fromstring(doc_xml)
            
            # 处理段落和表格
            sections = []
            positions = {}
            section_id = 1
            para_index = 1
            
            # 处理段落
            for p_node in root.findall('.//w:p', namespaces={'w': self.W_NS}):
                text = self.extract_text_from_paragraph(p_node)
                # 获取段落偏移量
                offset = self._get_element_offset(p_node)
                # 提取格式，包括样式继承
                formatting = self.extract_formatting(p_node)
                element_type = self.determine_element_type(p_node)
                
                section = DocumentSection(
                    id=section_id,
                    element_type=element_type,
                    raw_text=text,
                    formatting=formatting,
                    xml_path=f"word/document.xml#/w:document/w:body/w:p[{para_index}]",
                    offset=offset
                )
                
                # 生成位置信息
                position = {
                    "x": 72.0,
                    "y": 72.0 + (para_index * 18),
                    "width": 576.0,
                    "height": 18.0,
                    "page_number": (para_index // 50) + 1
                }
                
                sections.append(section)
                positions[section_id] = position
                section_id += 1
                para_index += 1
            
            # 处理表格
            table_index = 1
            for tbl_node in root.findall('.//w:tbl', namespaces={'w': self.W_NS}):
                text = self.extract_table_text(tbl_node)
                offset = self._get_element_offset(tbl_node)
                formatting = self.extract_formatting_for_table(tbl_node)
                
                section = DocumentSection(
                    id=section_id,
                    element_type=ElementType.Table,
                    raw_text=text,
                    formatting=formatting,
                    xml_path=f"word/document.xml#/w:document/w:body/w:tbl[{table_index}]",
                    offset=offset
                )
                
                # 生成位置信息
                position = {
                    "x": 72.0,
                    "y": 72.0 + (section_id * 24),
                    "width": 576.0,
                    "height": 72.0,
                    "page_number": (section_id // 25) + 1
                }
                
                sections.append(section)
                positions[section_id] = position
                section_id += 1
                table_index += 1
            
            # 提取元数据
            metadata = self.extract_metadata(archive, path)
            
            return {
                "sections": sections,
                "positions": positions,
                "metadata": metadata
            }
    
    def _parse_styles(self, archive):
        """解析样式文件，建立样式继承关系"""
        try:
            with archive.open('word/styles.xml') as f:
                styles_xml = f.read().decode('utf-8')
                root = ET.fromstring(styles_xml)
                
                # 解析样式
                for style_node in root.findall('.//w:style', namespaces={'w': self.STYLES_NS}):
                    style_id = style_node.attrib.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}styleId')
                    if not style_id:
                        continue
                    
                    # 解析样式名称
                    name_node = style_node.find('w:name', namespaces={'w': self.STYLES_NS})
                    style_name = name_node.attrib.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val', '') if name_node else ''
                    
                    # 解析基于的样式
                    based_on = None
                    based_on_node = style_node.find('w:basedOn', namespaces={'w': self.STYLES_NS})
                    if based_on_node:
                        based_on = based_on_node.attrib.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val')
                    
                    # 解析样式属性
                    properties = {}
                    
                    # 段落属性
                    p_pr = style_node.find('w:pPr', namespaces={'w': self.STYLES_NS})
                    if p_pr:
                        # 缩进
                        ind = p_pr.find('w:ind', namespaces={'w': self.STYLES_NS})
                        if ind:
                            if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}left' in ind.attrib:
                                left = ind.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}left']
                                try:
                                    val = float(left)
                                    properties['indent-left'] = f"{val / 20:.1f}pt"
                                except ValueError:
                                    properties['indent-left'] = left
                            if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}firstLine' in ind.attrib:
                                first_line = ind.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}firstLine']
                                try:
                                    val = float(first_line)
                                    properties['first-line-indent'] = f"{val / 20:.1f}pt"
                                except ValueError:
                                    properties['first-line-indent'] = first_line
                        
                        # 行间距
                        spacing = p_pr.find('w:spacing', namespaces={'w': self.STYLES_NS})
                        if spacing and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}line' in spacing.attrib:
                            line_spacing = spacing.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}line']
                            try:
                                val = float(line_spacing)
                                normalized = val / 240.0
                                properties['line-spacing'] = f"{normalized:.1f}"
                            except ValueError:
                                properties['line-spacing'] = line_spacing
                    
                    # 字符属性
                    r_pr = style_node.find('w:rPr', namespaces={'w': self.STYLES_NS})
                    if r_pr:
                        # 字体大小
                        sz = r_pr.find('w:sz', namespaces={'w': self.STYLES_NS})
                        if sz and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val' in sz.attrib:
                            val = sz.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val']
                            try:
                                sz_val = float(val)
                                properties['font-size'] = f"{sz_val / 2:.1f}pt"
                            except ValueError:
                                properties['font-size'] = val
                        
                        # 字体
                        r_fonts = r_pr.find('w:rFonts', namespaces={'w': self.STYLES_NS})
                        if r_fonts:
                            if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}ascii' in r_fonts.attrib:
                                properties['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}ascii']
                            elif '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia' in r_fonts.attrib:
                                properties['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia']
                            elif '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}hAnsi' in r_fonts.attrib:
                                properties['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}hAnsi']
                        
                        # 字体粗细
                        b = r_pr.find('w:b', namespaces={'w': self.STYLES_NS})
                        if b:
                            properties['font-weight'] = 'bold'
                        else:
                            properties['font-weight'] = 'normal'
                    
                    self.styles[style_id] = properties
                    if based_on:
                        self.style_inheritance[style_id] = based_on
        except KeyError:
            # 样式文件不存在，使用默认样式
            pass
    
    def _get_inherited_styles(self, style_id):
        """获取继承的样式属性"""
        if style_id not in self.styles:
            return {}
        
        properties = self.styles[style_id].copy()
        current_style = style_id
        
        # 递归获取父样式
        while current_style in self.style_inheritance:
            parent_style = self.style_inheritance[current_style]
            if parent_style in self.styles:
                parent_properties = self.styles[parent_style]
                # 只添加当前样式中没有的属性
                for key, value in parent_properties.items():
                    if key not in properties:
                        properties[key] = value
                current_style = parent_style
            else:
                break
        
        return properties
    
    def _get_element_offset(self, node):
        """获取元素在 XML 中的偏移量"""
        # 这里简化实现，实际应该计算元素在原始 XML 中的位置
        return id(node)
    
    def extract_text_from_paragraph(self, p_node) -> str:
        text_parts = []
        for t_node in p_node.findall('.//w:t', namespaces={'w': self.W_NS}):
            if t_node.text:
                trimmed = t_node.text.strip()
                if trimmed:
                    text_parts.append(trimmed)
        return " ".join(text_parts)
    
    def extract_formatting(self, p_node) -> Dict[str, str]:
        formatting = {}
        
        # 段落属性
        p_pr = p_node.find('w:pPr', namespaces={'w': self.W_NS})
        if p_pr is not None:
            # 段落样式
            p_style = p_pr.find('w:pStyle', namespaces={'w': self.W_NS})
            if p_style is not None and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val' in p_style.attrib:
                style_id = p_style.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val']
                # 获取继承的样式
                inherited_styles = self._get_inherited_styles(style_id)
                formatting.update(inherited_styles)
                formatting['style'] = style_id
            
            # 缩进
            ind = p_pr.find('w:ind', namespaces={'w': self.W_NS})
            if ind is not None:
                if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}left' in ind.attrib:
                    left = ind.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}left']
                    try:
                        val = float(left)
                        formatting['indent-left'] = f"{val / 20:.1f}pt"
                    except ValueError:
                        formatting['indent-left'] = left
                if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}firstLine' in ind.attrib:
                    first_line = ind.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}firstLine']
                    try:
                        val = float(first_line)
                        formatting['first-line-indent'] = f"{val / 20:.1f}pt"
                    except ValueError:
                        formatting['first-line-indent'] = first_line
            
            # 行间距
            spacing = p_pr.find('w:spacing', namespaces={'w': self.W_NS})
            if spacing is not None and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}line' in spacing.attrib:
                line_spacing = spacing.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}line']
                try:
                    val = float(line_spacing)
                    normalized = val / 240.0
                    formatting['line-spacing'] = f"{normalized:.1f}"
                except ValueError:
                    formatting['line-spacing'] = line_spacing
            
            # 标题级别
            outline_lvl = p_pr.find('w:outlineLvl', namespaces={'w': self.W_NS})
            if outline_lvl is not None and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val' in outline_lvl.attrib:
                formatting['outline-level'] = outline_lvl.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val']
        
        # 运行属性 - 遍历所有运行元素，找到第一个有格式的运行
        for run in p_node.findall('.//w:r', namespaces={'w': self.W_NS}):
            r_pr = run.find('w:rPr', namespaces={'w': self.W_NS})
            if r_pr is not None:
                # 字体大小
                sz = r_pr.find('w:sz', namespaces={'w': self.W_NS})
                if sz is not None and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val' in sz.attrib:
                    val = sz.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val']
                    try:
                        sz_val = float(val)
                        formatting['font-size'] = f"{sz_val / 2:.1f}pt"
                    except ValueError:
                        formatting['font-size'] = val
            
                # 字体
                r_fonts = r_pr.find('w:rFonts', namespaces={'w': self.W_NS})
                if r_fonts is not None:
                    if '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}ascii' in r_fonts.attrib:
                        formatting['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}ascii']
                    elif '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia' in r_fonts.attrib:
                        formatting['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia']
                    elif '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}hAnsi' in r_fonts.attrib:
                        formatting['font-family'] = r_fonts.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}hAnsi']
            
                # 字体粗细
                b = r_pr.find('w:b', namespaces={'w': self.W_NS})
                if b is not None:
                    formatting['font-weight'] = 'bold'
                else:
                    formatting['font-weight'] = 'normal'
            
                # 一旦找到有格式的运行，就停止搜索
                if formatting:
                    break
        
        # 确保至少有基本的格式信息
        if 'font-size' not in formatting:
            formatting['font-size'] = '12pt'
        if 'font-family' not in formatting:
            formatting['font-family'] = 'Arial'
        if 'font-weight' not in formatting:
            formatting['font-weight'] = 'normal'
        
        return formatting
    
    def determine_element_type(self, p_node):
        # 检查标题
        p_pr = p_node.find('w:pPr', namespaces={'w': self.W_NS})
        if p_pr is not None:
            outline_lvl = p_pr.find('w:outlineLvl', namespaces={'w': self.W_NS})
            if outline_lvl is not None and '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val' in outline_lvl.attrib:
                try:
                    level = int(outline_lvl.attrib['{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val'])
                    return ElementType.Heading(level)
                except ValueError:
                    pass
        
        # 检查表格
        if p_node.find('w:tbl', namespaces={'w': self.W_NS}) is not None:
            return ElementType.Table
        
        # 检查公式
        if p_node.find('w:oMath', namespaces={'w': self.W_NS}) is not None:
            return ElementType.Equation
        
        # 默认段落
        return ElementType.Paragraph
    
    def extract_table_text(self, tbl_node) -> str:
        text_parts = []
        for tr_node in tbl_node.findall('w:tr', namespaces={'w': self.W_NS}):
            for tc_node in tr_node.findall('w:tc', namespaces={'w': self.W_NS}):
                for p_node in tc_node.findall('w:p', namespaces={'w': self.W_NS}):
                    cell_text = self.extract_text_from_paragraph(p_node)
                    if cell_text:
                        text_parts.append(cell_text)
        if not text_parts:
            return "[表格]"
        return "\n".join(text_parts)
    
    def extract_formatting_for_table(self, tbl_node) -> Dict[str, str]:
        formatting = {}
        formatting['element-type'] = 'table'
        
        # 确保表格也包含字体信息
        formatting['font-size'] = '12pt'
        formatting['font-family'] = 'Arial'
        formatting['font-weight'] = 'normal'
        
        return formatting
    
    def extract_metadata(self, archive, path: Path) -> Dict[str, Any]:
        metadata = {
            'title': None,
            'total_pages': 0,
            'file_path': str(path),
            'file_size': path.stat().st_size,
            'creation_date': None,
            'modification_date': None
        }
        
        # 尝试读取 docProps/core.xml
        try:
            with archive.open('docProps/core.xml') as f:
                core_xml = f.read().decode('utf-8')
                root = ET.fromstring(core_xml)
                
                # 提取标题
                title = root.find('.//{http://purl.org/dc/elements/1.1/}title')
                if title is not None and title.text:
                    metadata['title'] = title.text.strip()
                
                # 提取创建日期
                created = root.find('.//{http://purl.org/dc/terms/}created')
                if created is not None and created.text:
                    metadata['creation_date'] = created.text.strip()
                
                # 提取修改日期
                modified = root.find('.//{http://purl.org/dc/terms/}modified')
                if modified is not None and modified.text:
                    metadata['modification_date'] = modified.text.strip()
        except KeyError:
            pass
        
        return metadata
