# Real-Time Chat Application with gRPC Bidirectional Streaming

A high-performance, real-time chat service built with **Spring Boot 3**, **gRPC**, **Protocol Buffers**, and **PostgreSQL**. The service leverages HTTP/2 bidirectional streaming for ultra-low latency, concurrent connection handling, and message history persistence.

---

## Features

- **Bidirectional Streaming**: Real-time communication using gRPC bidirectional streams over HTTP/2.
- **Protocol Buffers**: Strongly-typed contract definition (`chat.proto`) generating Java classes.
- **Concurrent Connection Management**: Thread-safe active room and user session tracking with synchronized observers.
- **Persistence**: Non-system chat messages persisted to PostgreSQL (`chat_messages` table).
- **REST Management Endpoints**: RESTful APIs for room creation, listing active rooms, and message history retrieval.
- **Containerization**: Production-ready `Dockerfile` and `docker-compose.yml` with healthchecks.
- **Integration Test Client**: Executable `chat-client.sh` with commented `grpcurl` commands.

---

## Project Structure

```
├── .env.example              # Environment variables template
├── .gitattributes            # Line endings configuration
├── .gitignore                # Git ignore rules
├── Dockerfile                # Multi-stage Docker build
├── docker-compose.yml        # Docker Compose configuration (app + db)
├── chat-client.sh            # Shell script with grpcurl commands
├── pom.xml                   # Maven build configuration
├── README.md                 # Detailed documentation
└── src
    ├── main
    │   ├── proto
    │   │   └── chat.proto    # Protobuf schema definition
    │   ├── java/com/example/grpc/chat
    │   │   ├── ChatApplication.java
    │   │   ├── controller
    │   │   │   └── RoomRestController.java
    │   │   ├── dto
    │   │   │   ├── CreateRoomRequest.java
    │   │   │   ├── MessageHistoryDto.java
    │   │   │   ├── RoomResponse.java
    │   │   │   └── RoomSummaryDto.java
    │   │   ├── model
    │   │   │   ├── ChatMessageEntity.java
    │   │   │   └── ChatRoomEntity.java
    │   │   ├── repository
    │   │   │   ├── ChatMessageRepository.java
    │   │   │   └── ChatRoomRepository.java
    │   │   └── service
    │   │       ├── ChatGrpcService.java
    │   │       └── ChatRoomManager.java
    │   └── resources
    │       ├── application.yml
    │       └── schema.sql
    └── test
        ├── java/com/example/grpc/chat
        │   └── ChatApplicationTests.java
        └── resources
            └── application.yml
```

---

## Core Protocol Buffers Schema (`chat.proto`)

```proto
syntax = "proto3";

package chat;

option java_multiple_files = true;
option java_package = "com.example.grpc.chat";
option java_outer_classname = "ChatProto";

service ChatService {
  rpc JoinRoom(JoinRoomRequest) returns (stream ChatMessage);
  rpc ChatStream(stream ChatMessage) returns (stream ChatMessage);
  rpc GetRoomUsers(RoomRequest) returns (UserListResponse);
  rpc LeaveRoom(LeaveRoomRequest) returns (LeaveRoomResponse);
}

message ChatMessage {
  string message_id = 1;
  string room_id = 2;
  string user_id = 3;
  string username = 4;
  string content = 5;
  int64 timestamp = 6;
  MessageType type = 7;
}

enum MessageType {
  TEXT = 0;
  IMAGE_URL = 1;
  SYSTEM_JOIN = 2;
  SYSTEM_LEAVE = 3;
}

message JoinRoomRequest {
  string room_id = 1;
  string user_id = 2;
  string username = 3;
}

message RoomRequest {
  string room_id = 1;
}

message UserInfo {
  string user_id = 1;
  string username = 2;
}

message UserListResponse {
  repeated UserInfo users = 1;
}

message LeaveRoomRequest {
  string room_id = 1;
  string user_id = 2;
}

message LeaveRoomResponse {
  bool success = 1;
  string message = 2;
}
```

---

## Environment Variables Configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

| Variable | Description | Example / Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://db:5432/chatdb` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `user` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `password` |
| `POSTGRES_DB` | PostgreSQL database name | `chatdb` |
| `POSTGRES_USER` | PostgreSQL user | `user` |
| `POSTGRES_PASSWORD` | PostgreSQL password | `password` |
| `SERVER_PORT` | REST API HTTP port | `8080` |
| `GRPC_SERVER_PORT` | gRPC server port | `9090` |

---

## Running with Docker Compose

1. **Build and start services**:
   ```bash
   docker-compose up --build -d
   ```
2. **Verify containers are running**:
   ```bash
   docker-compose ps
   ```
   Both `chat-db` and `chat-app` will be healthy and running.
3. **View logs**:
   ```bash
   docker-compose logs -f app
   ```
4. **Stop services**:
   ```bash
   docker-compose down -v
   ```

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.8+
- PostgreSQL database running on port 5432 (or configured port)
- `grpcurl` (optional, for CLI interaction)

### Build and Run
```bash
# 1. Compile protobuf files and build JAR
mvn clean package -DskipTests

# 2. Run unit and integration tests
mvn test

# 3. Start the application
java -jar target/bidirectional-streaming-1.0.0.jar
```

---

## REST API Reference

### 1. Create Room
- **Endpoint**: `POST /api/rooms`
- **Request Body**:
  ```json
  {
    "roomName": "general",
    "description": "General discussion channel"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "roomId": "general",
    "roomName": "general",
    "description": "General discussion channel"
  }
  ```

### 2. List Active Rooms
- **Endpoint**: `GET /api/rooms`
- **Response** (`200 OK`):
  ```json
  [
    {
      "roomId": "general",
      "roomName": "general",
      "userCount": 2
    }
  ]
  ```

### 3. Retrieve Message History
- **Endpoint**: `GET /api/rooms/{roomId}/messages/history`
- **Response** (`200 OK`):
  ```json
  [
    {
      "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "roomId": "general",
      "userId": "user-A",
      "username": "Alice",
      "content": "Hello World",
      "timestamp": "2026-10-06T12:34:56.789Z",
      "type": "TEXT"
    }
  ]
  ```
  Returns up to 100 messages sorted in reverse chronological order.

---

## gRPC RPC Reference & grpcurl Testing

Ensure gRPC server is listening on port `9090`.

### 1. List Services via Reflection
```bash
grpcurl -plaintext localhost:9090 list
```

### 2. Join a Room (`JoinRoom`)
Subscribes to a room stream and receives arrival announcements and subsequent broadcasts:
```bash
grpcurl -plaintext -d '{"room_id":"general","user_id":"user-A","username":"Alice"}' localhost:9090 chat.ChatService/JoinRoom
```
*Output*:
```json
{
  "message_id": "...",
  "room_id": "general",
  "user_id": "user-A",
  "username": "Alice",
  "content": "Alice joined the room",
  "timestamp": "1728217000000",
  "type": "SYSTEM_JOIN"
}
```

### 3. Send Message via Bidirectional Stream (`ChatStream`)
```bash
echo '{"room_id":"general","user_id":"user-A","username":"Alice","content":"Hello World","type":"TEXT"}' | \
  grpcurl -plaintext -d @ localhost:9090 chat.ChatService/ChatStream
```

### 4. Query Connected Users (`GetRoomUsers`)
```bash
grpcurl -plaintext -d '{"room_id":"general"}' localhost:9090 chat.ChatService/GetRoomUsers
```
*Output*:
```json
{
  "users": [
    {
      "user_id": "user-A",
      "username": "Alice"
    }
  ]
}
```

### 5. Leave Room (`LeaveRoom`)
```bash
grpcurl -plaintext -d '{"room_id":"general","user_id":"user-A"}' localhost:9090 chat.ChatService/LeaveRoom
```

---

## Automated Test Script (`chat-client.sh`)

An interactive script `chat-client.sh` is provided.

```bash
# Make executable
chmod +x chat-client.sh

# Run end-to-end demo interaction
./chat-client.sh demo

# Or use specific commands:
./chat-client.sh join general user-1 Alice
./chat-client.sh send general user-1 Alice "Hello from script"
./chat-client.sh users general
./chat-client.sh leave general user-1
```

---

## Database Schema (`chat_messages`)

Created automatically by JPA / `schema.sql`:

```sql
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL UNIQUE,
    room_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);
```
