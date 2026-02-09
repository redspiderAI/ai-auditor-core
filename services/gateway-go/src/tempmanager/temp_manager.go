package tempmanager

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// TempFileManager handles temporary file lifecycle management
type TempFileManager struct {
	TempDir       string
	cleanupTicker *time.Ticker
	stopCleanup   chan bool
	mutex         sync.RWMutex
	trackedFiles  map[string]time.Time // file path -> creation time
}

// NewTempFileManager creates a new temporary file manager
func NewTempFileManager(tempDir string) *TempFileManager {
	manager := &TempFileManager{
		TempDir:      tempDir,
		stopCleanup:  make(chan bool),
		trackedFiles: make(map[string]time.Time),
	}

	// Ensure the temp directory exists
	err := os.MkdirAll(tempDir, 0755)
	if err != nil {
		log.Printf("Failed to create temp directory: %v", err)
	}

	return manager
}

// TrackFile adds a file to the tracking list
func (tm *TempFileManager) TrackFile(filePath string) {
	tm.mutex.Lock()
	defer tm.mutex.Unlock()
	tm.trackedFiles[filePath] = time.Now()
}

// UntrackFile removes a file from the tracking list
func (tm *TempFileManager) UntrackFile(filePath string) {
	tm.mutex.Lock()
	defer tm.mutex.Unlock()
	delete(tm.trackedFiles, filePath)
}

// CleanupOldFiles removes files older than the specified age
func (tm *TempFileManager) CleanupOldFiles(maxAge time.Duration) error {
	tm.mutex.Lock()
	defer tm.mutex.Unlock()

	cutoffTime := time.Now().Add(-maxAge)

	// Clean up tracked files
	for filePath, createTime := range tm.trackedFiles {
		if createTime.Before(cutoffTime) {
			if err := os.Remove(filePath); err != nil {
				log.Printf("Failed to remove old temp file %s: %v", filePath, err)
			} else {
				delete(tm.trackedFiles, filePath)
				log.Printf("Removed old temp file: %s", filePath)
			}
		}
	}

	// Also scan the temp directory for untracked files that might have been left behind
	return tm.scanAndCleanupUntracked(maxAge)
}

// scanAndCleanupUntracked scans the temp directory for files that aren't tracked and cleans them up if they're too old
func (tm *TempFileManager) scanAndCleanupUntracked(maxAge time.Duration) error {
	cutoffTime := time.Now().Add(-maxAge)

	err := filepath.Walk(tm.TempDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		// Skip directories
		if info.IsDir() {
			return nil
		}

		// Check if this file is tracked
		_, isTracked := tm.trackedFiles[path]
		if isTracked {
			return nil // Skip tracked files as they're handled separately
		}

		// Check if the file is older than the cutoff
		if info.ModTime().Before(cutoffTime) {
			// Check if it looks like a temp file from our system (has UUID-like pattern or specific suffix)
			if tm.isOurTempFile(filepath.Base(path)) {
				if err := os.Remove(path); err != nil {
					log.Printf("Failed to remove untracked temp file %s: %v", path, err)
				} else {
					log.Printf("Removed untracked old temp file: %s", path)
				}
			}
		}

		return nil
	})

	return err
}

// isOurTempFile checks if a filename follows our temp file naming pattern
func (tm *TempFileManager) isOurTempFile(filename string) bool {
	// Check for common temp file patterns used in our system
	return strings.Contains(filename, "-annotated.docx") || 
		   strings.Contains(filename, "-report.json") ||
		   strings.HasSuffix(filename, ".docx")
}

// CleanupTaskFiles removes all files associated with a specific task
func (tm *TempFileManager) CleanupTaskFiles(taskID string) {
	tm.mutex.Lock()
	defer tm.mutex.Unlock()

	var filesToRemove []string

	// Find all files related to this task
	for filePath := range tm.trackedFiles {
		filename := filepath.Base(filePath)
		if strings.Contains(filename, taskID) {
			filesToRemove = append(filesToRemove, filePath)
		}
	}

	// Remove the files
	for _, filePath := range filesToRemove {
		if err := os.Remove(filePath); err != nil {
			log.Printf("Failed to remove task file %s: %v", filePath, err)
		} else {
			delete(tm.trackedFiles, filePath)
			log.Printf("Removed task file: %s", filePath)
		}
	}
}

// StartCleanupDaemon starts a background goroutine that periodically cleans up old files
func (tm *TempFileManager) StartCleanupDaemon(interval time.Duration, maxAge time.Duration) {
	tm.cleanupTicker = time.NewTicker(interval)
	
	go func() {
		for {
			select {
			case <-tm.cleanupTicker.C:
				if err := tm.CleanupOldFiles(maxAge); err != nil {
					log.Printf("Error during periodic cleanup: %v", err)
				}
			case <-tm.stopCleanup:
				tm.cleanupTicker.Stop()
				return
			}
		}
	}()
}

// StopCleanupDaemon stops the background cleanup daemon
func (tm *TempFileManager) StopCleanupDaemon() {
	if tm.stopCleanup != nil {
		close(tm.stopCleanup)
	}
}

// CleanupAll removes all tracked files and stops the cleanup daemon
func (tm *TempFileManager) CleanupAll() error {
	tm.StopCleanupDaemon()

	tm.mutex.Lock()
	defer tm.mutex.Unlock()

	var errs []error
	for filePath := range tm.trackedFiles {
		if err := os.Remove(filePath); err != nil {
			log.Printf("Failed to remove temp file %s: %v", filePath, err)
			errs = append(errs, err)
		} else {
			log.Printf("Removed temp file: %s", filePath)
		}
	}

	// Clear the tracked files map
	tm.trackedFiles = make(map[string]time.Time)

	if len(errs) > 0 {
		return fmt.Errorf("errors occurred during cleanup: %v", errs)
	}
	return nil
}

// GetDiskUsage returns the total size of tracked temporary files
func (tm *TempFileManager) GetDiskUsage() (int64, error) {
	tm.mutex.RLock()
	defer tm.mutex.RUnlock()

	var totalSize int64
	for filePath := range tm.trackedFiles {
		info, err := os.Stat(filePath)
		if err != nil {
			// File might have been deleted externally, skip it
			continue
		}
		totalSize += info.Size()
	}

	return totalSize, nil
}