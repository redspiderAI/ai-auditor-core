package worker

import (
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
)

// Worker simulates processing (parse -> audit -> report -> annotate).
func Worker(tasks <-chan string, s *store.Store) {
	for id := range tasks {
		if ok := s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Parsing"
			t.Progress = 10
		}); !ok {
			continue
		}

		time.Sleep(1 * time.Second) // simulate parse

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.Status = "Auditing"
			t.Progress = 40
		})

		for p := 50; p <= 90; p += 10 {
			time.Sleep(800 * time.Millisecond)
			_ = s.UpdateTask(id, func(t *store.Task) { t.Progress = p })
		}

		annotated := filepath.Join("..", "temp_docs", id+"-annotated.docx")
		report := filepath.Join("..", "temp_docs", id+"-report.json")

		if t, ok := s.GetTask(id); ok {
			_ = copyFile(t.SourcePath, annotated)
		}

		_ = store.WriteReport(report, map[string]any{
			"task_id":      id,
			"status":       "completed",
			"generated_at": time.Now().Format(time.RFC3339),
			"issues":       []any{},
		})

		_ = s.UpdateTask(id, func(t *store.Task) {
			t.AnnotatedPath = annotated
			t.ReportPath = report
			t.Status = "Completed"
			t.Progress = 100
		})
	}
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}
