from pathlib import Path
import json

from src.core.parser import DocxParser
from src.core.pdf_parser import PdfParser

def run_document_processing():
    # 处理 data/input 目录中的文档
    input_dir = Path('data/input')
    output_dir = Path('data/output')
    
    output_dir.mkdir(exist_ok=True)
    
    docx_parser = DocxParser()
    pdf_parser = PdfParser()
    
    for file_path in input_dir.iterdir():
        try:
            if file_path.suffix == '.docx':
                result = docx_parser.parse(file_path)
                output_file = output_dir / f"{file_path.stem}_parsed.json"
                # 保存结果为 JSON，添加自定义序列化函数
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump(result, f, ensure_ascii=False, indent=2, default=lambda o: o.__dict__ if hasattr(o, '__dict__') else str(o))
                print(f" Processed {file_path} -> {output_file}")
            elif file_path.suffix == '.pdf':
                result = pdf_parser.parse(file_path)
                output_file = output_dir / f"{file_path.stem}_parsed.json"
                # 保存结果为 JSON
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump(result, f, ensure_ascii=False, indent=2)
                print(f" Processed {file_path} -> {output_file}")
        except Exception as e:
            print(f" Failed to process {file_path}: {e}")
