import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List
import tempfile
import os

class ErrorItem:
    def __init__(self, paragraph_index: int, comment: str):
        self.paragraph_index = paragraph_index
        self.comment = comment

def inject_comments(docx_path: str, error_list: List[ErrorItem], output_path: str, author: str) -> bool:
    """注入批注到 DOCX 文件"""
    try:
        # 创建临时目录
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir_path = Path(temp_dir)
            
            # 解压 DOCX 文件
            with zipfile.ZipFile(docx_path, 'r') as zip_ref:
                zip_ref.extractall(temp_dir_path)
            
            # 处理批注
            comment_id = 1
            
            # 读取或创建 comments.xml
            comments_xml_path = temp_dir_path / 'word' / 'comments.xml'
            if comments_xml_path.exists():
                tree = ET.parse(comments_xml_path)
                root = tree.getroot()
            else:
                # 创建 comments.xml
                root = ET.Element('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}comments')
                tree = ET.ElementTree(root)
            
            # 读取 document.xml
            document_xml_path = temp_dir_path / 'word' / 'document.xml'
            doc_tree = ET.parse(document_xml_path)
            doc_root = doc_tree.getroot()
            
            # 注入批注
            for error in error_list:
                # 查找目标段落
                paragraphs = doc_root.findall('.//w:p', namespaces={'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'})
                if error.paragraph_index < len(paragraphs):
                    para = paragraphs[error.paragraph_index]
                    
                    # 创建评论
                    comment = ET.SubElement(root, '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}comment', {
                        '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}id': str(comment_id),
                        '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}author': author,
                        '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}date': '2023-01-01T00:00:00Z'
                    })
                    
                    # 添加评论内容
                    p = ET.SubElement(comment, '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p')
                    r = ET.SubElement(p, '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}r')
                    t = ET.SubElement(r, '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t')
                    t.text = error.comment
                    
                    # 在段落中添加评论引用
                    if para:
                        # 查找第一个运行元素
                        run = para.find('.//w:r', namespaces={'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'})
                        if run:
                            # 创建评论引用
                            comment_ref = ET.Element('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}commentReference', {
                                '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}id': str(comment_id)
                            })
                            # 插入到运行元素中
                            run.insert(0, comment_ref)
                    
                    comment_id += 1
            
            # 保存 comments.xml
            tree.write(comments_xml_path, encoding='utf-8', xml_declaration=True)
            
            # 保存 document.xml
            doc_tree.write(document_xml_path, encoding='utf-8', xml_declaration=True)
            
            # 更新 document.xml.rels
            rels_path = temp_dir_path / 'word' / '_rels' / 'document.xml.rels'
            if rels_path.exists():
                rels_tree = ET.parse(rels_path)
                rels_root = rels_tree.getroot()
                
                # 检查是否已经有 comments.xml 的引用
                has_comments = False
                for rel in rels_root.findall('.//{http://schemas.openxmlformats.org/package/2006/relationships}Relationship', 
                                         namespaces={'r': 'http://schemas.openxmlformats.org/package/2006/relationships'}):
                    if rel.attrib.get('{http://schemas.openxmlformats.org/package/2006/relationships}Target') == 'comments.xml':
                        has_comments = True
                        break
                
                # 如果没有，添加引用
                if not has_comments:
                    # 生成新的关系 ID
                    rel_id = f"rId{len(rels_root.findall('.//{http://schemas.openxmlformats.org/package/2006/relationships}Relationship')) + 1}"
                    ET.SubElement(rels_root, '{http://schemas.openxmlformats.org/package/2006/relationships}Relationship', {
                        '{http://schemas.openxmlformats.org/package/2006/relationships}Id': rel_id,
                        '{http://schemas.openxmlformats.org/package/2006/relationships}Type': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments',
                        '{http://schemas.openxmlformats.org/package/2006/relationships}Target': 'comments.xml'
                    })
                    rels_tree.write(rels_path, encoding='utf-8', xml_declaration=True)
            
            # 重新打包成 DOCX
            with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zip_ref:
                for root, _, files in os.walk(temp_dir_path):
                    for file in files:
                        file_path = Path(root) / file
                        arcname = str(file_path.relative_to(temp_dir_path))
                        zip_ref.write(file_path, arcname)
            
            print(f" 批注已成功注入到：{output_path}")
            return True
    except Exception as e:
        print(f" 批注注入失败：{e}")
        import traceback
        traceback.print_exc()
        return False
