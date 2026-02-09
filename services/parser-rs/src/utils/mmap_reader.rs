use memmap2::Mmap;
use std::fs::File;
use std::io::Read;

pub struct MmapFileReader {
    _file: File,
    mmap: Mmap,
}

impl MmapFileReader {
    pub fn new(file_path: &str) -> std::io::Result<Self> {
        let file = File::open(file_path)?;
        let mmap = unsafe { Mmap::map(&file)? };

        Ok(MmapFileReader { _file: file, mmap })
    }

    pub fn as_slice(&self) -> &[u8] {
        &self.mmap
    }

    pub fn len(&self) -> usize {
        self.mmap.len()
    }

    pub fn is_empty(&self) -> bool {
        self.mmap.is_empty()
    }
}

impl Read for MmapFileReader {
    fn read(&mut self, _buf: &mut [u8]) -> std::io::Result<usize> {
        Err(std::io::Error::new(
            std::io::ErrorKind::Other,
            "MmapFileReader does not support sequential reading",
        ))
    }
}

pub fn create_mmap_reader(file_path: &str) -> std::io::Result<MmapFileReader> {
    MmapFileReader::new(file_path)
}
