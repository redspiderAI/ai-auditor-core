import PyPDF2
from pathlib import Path
from typing import Dict, Any

class PdfParser:
    def parse(self, path: Path) -> Dict[str, Any]:
        sections = []
        positions = {}
        section_id = 1
        
        with open(path, 'rb') as f:
            reader = PyPDF2.PdfReader(f)
            num_pages = len(reader.pages)
            
            for page_num in range(num_pages):
                page = reader.pages[page_num]
                text = page.extract_text()
                
                if text:
                    paragraphs = text.split('\n')
                    for para_index, paragraph in enumerate(paragraphs):
                        if paragraph.strip():
                            # 确保每个元素都包含格式信息
                            section = {
                                'id': section_id,
                                'element_type': 'Paragraph',
                                'raw_text': paragraph.strip(),
                                'formatting': {
                                    'element-type': 'pdf-text',
                                    'font-size': '12pt',  # 默认字体大小
                                    'font-family': 'Arial',  # 默认字体类型
                                    'font-weight': 'normal'  # 默认字体粗细
                                },
                                'xml_path': f'page_{page_num + 1}_para_{para_index + 1}.txt',
                                'offset': id(paragraph)  # 添加偏移量
                            }
                            
                            # 生成位置信息
                            position = {
                                'x': 50.0,
                                'y': 50.0 + (para_index * 20),
                                'width': 500.0,
                                'height': 15.0,
                                'page_number': page_num + 1
                            }
                            
                            sections.append(section)
                            positions[section_id] = position
                            section_id += 1
        
        metadata = {
            'title': None,
            'total_pages': len(reader.pages) if 'reader' in locals() else 0,
            'file_path': str(path),
            'file_size': path.stat().st_size,
            'creation_date': None,
            'modification_date': None
        }
        
        return {
            'sections': sections,
            'positions': positions,
            'metadata': metadata
        }
