use memmap2::Mmap;
use std::fs::File;
use std::io::{BufReader, Read};

pub struct MmapFileReader {
    _file: File,
    mmap: Mmap,
}

impl MmapFileReader {
    pub fn new(file_path: &str) -> std::io::Result<Self> {
        let file = File::open(file_path)?;
        let mmap = unsafe { Mmap::map(&file)? };
        
        Ok(MmapFileReader {
            _file: file,
            mmap,
        })
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
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        // Since we have the entire file mapped in memory, we can't really implement
        // a traditional read here. Instead, clients should access the slice directly.
        Err(std::io::Error::new(
            std::io::ErrorKind::Other,
            "MmapFileReader does not support sequential reading",
        ))
    }
}

pub fn create_mmap_reader(file_path: &str) -> std::io::Result<MmapFileReader> {
    MmapFileReader::new(file_path)
}