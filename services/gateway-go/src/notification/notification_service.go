package notification

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/redspiderAI/ai-auditor-core/services/gateway-go/src/store"
)

// NotificationType defines the type of notification
type NotificationType string

const (
	TaskStatusUpdate NotificationType = "task_status_update"
	TaskCompleted    NotificationType = "task_completed"
	TaskError        NotificationType = "task_error"
)

// Notification represents a notification message
type Notification struct {
	Type      NotificationType `json:"type"`
	TaskID    string          `json:"task_id"`
	Status    store.TaskStatus `json:"status"`
	Progress  int             `json:"progress"`
	Timestamp time.Time       `json:"timestamp"`
	Message   string          `json:"message,omitempty"`
}

// ClientManager manages WebSocket clients
type ClientManager struct {
	clients    map[*Client]bool
	broadcast  chan Notification
	register   chan *Client
	unregister chan *Client
	mutex      sync.RWMutex
}

// Client represents a WebSocket client
type Client struct {
	conn   *websocket.Conn
	send   chan Notification
	manager *ClientManager
	taskID string
}

// NewClientManager creates a new client manager
func NewClientManager() *ClientManager {
	return &ClientManager{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan Notification),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

// Start begins the client manager loop
func (cm *ClientManager) Start() {
	go func() {
		for {
			select {
			case client := <-cm.register:
				cm.mutex.Lock()
				cm.clients[client] = true
				cm.mutex.Unlock()
				log.Printf("Client registered for task %s", client.taskID)
				
			case client := <-cm.unregister:
				cm.mutex.Lock()
				if _, ok := cm.clients[client]; ok {
					delete(cm.clients, client)
					close(client.send)
				}
				cm.mutex.Unlock()
				log.Printf("Client unregistered for task %s", client.taskID)
				
			case notification := <-cm.broadcast:
				cm.mutex.RLock()
				for client := range cm.clients {
					// Only send notifications relevant to the client's task
					if client.taskID == notification.TaskID {
						select {
						case client.send <- notification:
						default:
							// Close connection if channel is full
							close(client.send)
							delete(cm.clients, client)
						}
					}
				}
				cm.mutex.RUnlock()
			}
		}
	}()
}

// SendNotification sends a notification to clients interested in a specific task
func (cm *ClientManager) SendNotification(notification Notification) {
	cm.broadcast <- notification
}

// ServeWs handles WebSocket connections
func (cm *ClientManager) ServeWs(w http.ResponseWriter, r *http.Request, taskID string) {
	conn, err := websocket.Upgrade(w, r, nil, 1024, 1024)
	if err != nil {
		log.Printf("WebSocket upgrade error: %v", err)
		return
	}

	client := &Client{
		conn:    conn,
		send:    make(chan Notification, 256), // Buffered channel
		manager: cm,
		taskID:  taskID,
	}

	cm.register <- client

	// Start sending messages to the client
	go client.writePump()

	// Start reading messages from the client (to handle disconnects)
	go client.readPump()
}

// writePump sends messages to the WebSocket connection
func (c *Client) writePump() {
	ticker := time.NewTicker(time.Second * 5) // Ping interval
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			if !ok {
				// Channel closed, send close message
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			w, err := c.conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}

			messageBytes, err := json.Marshal(message)
			if err != nil {
				log.Printf("Error marshaling notification: %v", err)
				return
			}

			if _, err := w.Write(messageBytes); err != nil {
				log.Printf("Error writing message: %v", err)
				return
			}

			if err := w.Close(); err != nil {
				return
			}
		case <-ticker.C:
			if err := c.conn.WriteMessage(websocket.PingMessage, []byte{}); err != nil {
				return
			}
		}
	}
}

// readPump reads messages from the WebSocket connection
func (c *Client) readPump() {
	defer func() {
		c.manager.unregister <- c
		c.conn.Close()
	}()

	// Set read deadline
	c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	for {
		_, _, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("WebSocket error: %v", err)
			}
			break
		}
	}
}

// NotificationService combines WebSocket functionality
type NotificationService struct {
	clientManager *ClientManager
}

// NewNotificationService creates a new notification service
func NewNotificationService() *NotificationService {
	service := &NotificationService{
		clientManager: NewClientManager(),
	}
	
	// Start the client manager
	service.clientManager.Start()
	
	return service
}

// ServeWs handles WebSocket connections for a specific task
func (ns *NotificationService) ServeWs(w http.ResponseWriter, r *http.Request, taskID string) {
	ns.clientManager.ServeWs(w, r, taskID)
}

// NotifyTaskUpdate sends notifications through all available channels
func (ns *NotificationService) NotifyTaskUpdate(taskID string, status store.TaskStatus, progress int, message string) {
	notification := Notification{
		Type:      TaskStatusUpdate,
		TaskID:    taskID,
		Status:    status,
		Progress:  progress,
		Timestamp: time.Now(),
		Message:   message,
	}

	// Send via WebSocket
	ns.clientManager.SendNotification(notification)
}

// NotifyTaskCompletion sends completion notifications
func (ns *NotificationService) NotifyTaskCompletion(taskID string) {
	notification := Notification{
		Type:      TaskCompleted,
		TaskID:    taskID,
		Status:    store.Completed,
		Progress:  100,
		Timestamp: time.Now(),
		Message:   "Task completed successfully",
	}

	// Send via WebSocket
	ns.clientManager.SendNotification(notification)
}

// NotifyTaskError sends error notifications
func (ns *NotificationService) NotifyTaskError(taskID string, errorMsg string) {
	notification := Notification{
		Type:      TaskError,
		TaskID:    taskID,
		Status:    store.Error,
		Progress:  0,
		Timestamp: time.Now(),
		Message:   errorMsg,
	}

	// Send via WebSocket
	ns.clientManager.SendNotification(notification)
}