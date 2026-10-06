package com.example.grpc.chat.service;

import com.example.grpc.chat.ChatMessage;
import com.example.grpc.chat.MessageType;
import com.example.grpc.chat.UserInfo;
import com.example.grpc.chat.model.ChatRoomEntity;
import com.example.grpc.chat.repository.ChatRoomRepository;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatRoomManager {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomManager.class);

    private final ChatRoomRepository chatRoomRepository;

    // Room ID -> Active Observers Set
    private final Map<String, Set<StreamObserver<ChatMessage>>> roomObservers = new ConcurrentHashMap<>();

    // Room ID -> (User ID -> Username)
    private final Map<String, Map<String, String>> roomUsers = new ConcurrentHashMap<>();

    // User ID + Room ID -> Set of Observers for that user in that room
    private final Map<String, Set<StreamObserver<ChatMessage>>> userObservers = new ConcurrentHashMap<>();

    // In-memory room metadata cache (roomId -> ChatRoomEntity)
    private final Map<String, ChatRoomEntity> roomCache = new ConcurrentHashMap<>();

    public ChatRoomManager(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
        // Pre-load rooms from DB on startup
        try {
            List<ChatRoomEntity> existingRooms = chatRoomRepository.findAll();
            for (ChatRoomEntity room : existingRooms) {
                roomCache.put(room.getRoomId(), room);
                roomObservers.putIfAbsent(room.getRoomId(), new CopyOnWriteArraySet<>());
                roomUsers.putIfAbsent(room.getRoomId(), new ConcurrentHashMap<>());
            }
        } catch (Exception e) {
            log.warn("Could not pre-load rooms from database on startup: {}", e.getMessage());
        }
    }

    public synchronized ChatRoomEntity createOrGetRoom(String roomId, String roomName, String description) {
        if (roomId == null || roomId.trim().isEmpty()) {
            if (roomName != null && !roomName.trim().isEmpty()) {
                roomId = roomName.trim().toLowerCase().replaceAll("[^a-zA-Z0-9_-]", "-");
            } else {
                roomId = UUID.randomUUID().toString();
            }
        }

        ChatRoomEntity existing = roomCache.get(roomId);
        if (existing != null) {
            return existing;
        }

        ChatRoomEntity entity = new ChatRoomEntity(roomId, roomName != null ? roomName : roomId, description);
        try {
            entity = chatRoomRepository.save(entity);
        } catch (Exception e) {
            log.warn("Error saving room to DB, keeping in memory: {}", e.getMessage());
        }

        roomCache.put(roomId, entity);
        roomObservers.putIfAbsent(roomId, new CopyOnWriteArraySet<>());
        roomUsers.putIfAbsent(roomId, new ConcurrentHashMap<>());
        return entity;
    }

    public void ensureRoomExists(String roomId, String defaultName) {
        if (!roomCache.containsKey(roomId)) {
            createOrGetRoom(roomId, defaultName != null ? defaultName : roomId, "Chat room " + roomId);
        }
    }

    public List<ChatRoomEntity> getAllRooms() {
        // Return all known rooms (combine DB and cache)
        Map<String, ChatRoomEntity> all = new LinkedHashMap<>(roomCache);
        try {
            for (ChatRoomEntity entity : chatRoomRepository.findAll()) {
                all.putIfAbsent(entity.getRoomId(), entity);
                roomCache.putIfAbsent(entity.getRoomId(), entity);
            }
        } catch (Exception e) {
            log.debug("Database findAll failed: {}", e.getMessage());
        }
        return new ArrayList<>(all.values());
    }

    public int getUserCount(String roomId) {
        Map<String, String> users = roomUsers.get(roomId);
        return users != null ? users.size() : 0;
    }

    public synchronized void registerUser(String roomId, String userId, String username, StreamObserver<ChatMessage> observer) {
        ensureRoomExists(roomId, roomId);

        roomUsers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, username);

        if (observer != null) {
            roomObservers.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(observer);
            String userKey = makeUserKey(roomId, userId);
            userObservers.computeIfAbsent(userKey, k -> new CopyOnWriteArraySet<>()).add(observer);
        }

        log.info("User {} ({}) joined room {}. Active users in room: {}", username, userId, roomId, getUserCount(roomId));
    }

    public synchronized void registerObserverOnly(String roomId, StreamObserver<ChatMessage> observer) {
        ensureRoomExists(roomId, roomId);
        if (observer != null) {
            roomObservers.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(observer);
        }
    }

    public synchronized String removeUser(String roomId, String userId) {
        Map<String, String> users = roomUsers.get(roomId);
        String username = null;
        if (users != null) {
            username = users.remove(userId);
        }

        String userKey = makeUserKey(roomId, userId);
        Set<StreamObserver<ChatMessage>> observers = userObservers.remove(userKey);
        if (observers != null) {
            Set<StreamObserver<ChatMessage>> allInRoom = roomObservers.get(roomId);
            for (StreamObserver<ChatMessage> obs : observers) {
                if (allInRoom != null) {
                    allInRoom.remove(obs);
                }
                try {
                    obs.onCompleted();
                } catch (Exception ignored) {
                }
            }
        }

        log.info("User {} ({}) removed from room {}. Active users in room: {}", username, userId, roomId, getUserCount(roomId));
        return username != null ? username : userId;
    }

    public synchronized void removeObserver(String roomId, StreamObserver<ChatMessage> observer) {
        Set<StreamObserver<ChatMessage>> observers = roomObservers.get(roomId);
        if (observers != null && observer != null) {
            observers.remove(observer);
        }
    }

    public synchronized void removeObserverGlobally(StreamObserver<ChatMessage> observer) {
        if (observer == null) return;
        for (Map.Entry<String, Set<StreamObserver<ChatMessage>>> entry : roomObservers.entrySet()) {
            entry.getValue().remove(observer);
        }
    }

    public List<UserInfo> getUsersInRoom(String roomId) {
        Map<String, String> users = roomUsers.get(roomId);
        List<UserInfo> list = new ArrayList<>();
        if (users != null) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                list.add(UserInfo.newBuilder()
                        .setUserId(entry.getKey())
                        .setUsername(entry.getValue())
                        .build());
            }
        }
        return list;
    }

    public void broadcast(String roomId, ChatMessage message) {
        Set<StreamObserver<ChatMessage>> observers = roomObservers.get(roomId);
        if (observers == null || observers.isEmpty()) {
            log.debug("No active observers in room {} to broadcast message {}", roomId, message.getMessageId());
            return;
        }

        List<StreamObserver<ChatMessage>> failedObservers = new ArrayList<>();
        for (StreamObserver<ChatMessage> observer : observers) {
            try {
                synchronized (observer) {
                    observer.onNext(message);
                }
            } catch (Exception e) {
                log.warn("Failed to broadcast message to an observer in room {}: {}", roomId, e.getMessage());
                failedObservers.add(observer);
            }
        }

        for (StreamObserver<ChatMessage> failed : failedObservers) {
            observers.remove(failed);
        }
    }

    private String makeUserKey(String roomId, String userId) {
        return roomId + "::" + userId;
    }

    @PreDestroy
    public void shutdown() {
        log.info("ChatRoomManager shutting down. Notifying and closing all connected clients...");
        for (Map.Entry<String, Set<StreamObserver<ChatMessage>>> entry : roomObservers.entrySet()) {
            String roomId = entry.getKey();
            Set<StreamObserver<ChatMessage>> observers = entry.getValue();
            ChatMessage shutdownMsg = ChatMessage.newBuilder()
                    .setMessageId(UUID.randomUUID().toString())
                    .setRoomId(roomId)
                    .setUserId("system")
                    .setUsername("SYSTEM")
                    .setContent("Server is shutting down.")
                    .setTimestamp(System.currentTimeMillis())
                    .setType(MessageType.SYSTEM_LEAVE)
                    .build();

            for (StreamObserver<ChatMessage> observer : observers) {
                try {
                    synchronized (observer) {
                        observer.onNext(shutdownMsg);
                        observer.onCompleted();
                    }
                } catch (Exception ignored) {
                }
            }
            observers.clear();
        }
        roomUsers.clear();
        userObservers.clear();
    }
}
