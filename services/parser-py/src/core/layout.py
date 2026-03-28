from typing import Dict, List, Optional

class PositionInfo:
    def __init__(self, x: float, y: float, width: float, height: float, page_number: Optional[int]):
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.page_number = page_number

class DocumentMetadata:
    def __init__(self, title: Optional[str], total_pages: int, file_path: str, file_size: int, 
                 creation_date: Optional[str], modification_date: Optional[str]):
        self.title = title
        self.total_pages = total_pages
        self.file_path = file_path
        self.file_size = file_size
        self.creation_date = creation_date
        self.modification_date = modification_date

class DocumentTree:
    def __init__(self, sections: List, positions: Dict[int, PositionInfo], metadata: DocumentMetadata):
        self.sections = sections
        self.positions = positions
        self.metadata = metadata
    
    @classmethod
    def new(cls):
        return cls([], {}, DocumentMetadata(None, 0, "", 0, None, None))
    
    def add_section_with_position(self, section, position: PositionInfo):
        self.sections.append(section)
        self.positions[section.id] = position
    
    def get_section_by_id(self, id: int):
        for section in self.sections:
            if section.id == id:
                return section
        return None
    
    def get_position_by_id(self, id: int):
        return self.positions.get(id)
    
    def update_section(self, id: int, new_content: str):
        for section in self.sections:
            if section.id == id:
                section.raw_text = new_content
                break
