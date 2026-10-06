package com.example.grpc.chat.service;

import com.example.grpc.chat.ChatMessage;
import com.example.grpc.chat.ChatServiceGrpc;
import com.example.grpc.chat.JoinRoomRequest;
import com.example.grpc.chat.LeaveRoomRequest;
import com.example.grpc.chat.LeaveRoomResponse;
import com.example.grpc.chat.MessageType;
import com.example.grpc.chat.RoomRequest;
import com.example.grpc.chat.UserInfo;
import com.example.grpc.chat.UserListResponse;
import com.example.grpc.chat.model.ChatMessageEntity;
import com.example.grpc.chat.repository.ChatMessageRepository;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@GrpcService
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ChatGrpcService.class);

    private final ChatRoomManager chatRoomManager;
    private final ChatMessageRepository chatMessageRepository;

    public ChatGrpcService(ChatRoomManager chatRoomManager, ChatMessageRepository chatMessageRepository) {
        this.chatRoomManager = chatRoomManager;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public void joinRoom(JoinRoomRequest request, StreamObserver<ChatMessage> responseObserver) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();
        String username = request.getUsername();

        log.info("Received JoinRoom request: roomId={}, userId={}, username={}", roomId, userId, username);

        // Register user and observer
        chatRoomManager.registerUser(roomId, userId, username, responseObserver);

        // Hook up cancel/disconnect listener if available
        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver<ChatMessage> serverCallObserver = (ServerCallStreamObserver<ChatMessage>) responseObserver;
            serverCallObserver.setOnCancelHandler(() -> {
                log.info("JoinRoom stream canceled by client for room {} user {}", roomId, userId);
                chatRoomManager.removeObserver(roomId, responseObserver);
            });
        }

        // Announce user arrival with SYSTEM_JOIN
        ChatMessage joinMessage = ChatMessage.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setRoomId(roomId)
                .setUserId(userId)
                .setUsername(username)
                .setContent(username + " joined the room")
                .setTimestamp(System.currentTimeMillis())
                .setType(MessageType.SYSTEM_JOIN)
                .build();

        // Broadcast to all observers in room (including the joining user)
        chatRoomManager.broadcast(roomId, joinMessage);
    }

    @Override
    public StreamObserver<ChatMessage> chatStream(StreamObserver<ChatMessage> responseObserver) {
        return new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage rawMessage) {
                String roomId = rawMessage.getRoomId();
                String userId = rawMessage.getUserId();
                String username = rawMessage.getUsername();

                // Generate messageId and timestamp if missing
                String messageId = rawMessage.getMessageId().isEmpty()
                        ? UUID.randomUUID().toString()
                        : rawMessage.getMessageId();

                long timestamp = rawMessage.getTimestamp() <= 0
                        ? System.currentTimeMillis()
                        : rawMessage.getTimestamp();

                MessageType type = rawMessage.getType();

                ChatMessage.Builder messageBuilder = rawMessage.toBuilder()
                        .setMessageId(messageId)
                        .setTimestamp(timestamp);

                ChatMessage processedMessage = messageBuilder.build();

                log.info("Received message in room {}: from {} ({}) - type: {}",
                        roomId, username, userId, type);

                // Register observer and active user in the room
                if (roomId != null && !roomId.isEmpty()) {
                    chatRoomManager.registerObserverOnly(roomId, responseObserver);
                    if (userId != null && !userId.isEmpty() && username != null && !username.isEmpty()) {
                        chatRoomManager.registerUser(roomId, userId, username, responseObserver);
                    }
                }

                // Persist non-system messages (TEXT and IMAGE_URL)
                if (type == MessageType.TEXT || type == MessageType.IMAGE_URL) {
                    try {
                        ChatMessageEntity entity = new ChatMessageEntity(
                                messageId,
                                roomId,
                                userId,
                                username,
                                processedMessage.getContent(),
                                type.name(),
                                Instant.ofEpochMilli(timestamp)
                        );
                        chatMessageRepository.save(entity);
                        log.debug("Persisted message {} to database", messageId);
                    } catch (Exception e) {
                        log.error("Failed to persist message to database: {}", e.getMessage(), e);
                    }
                }

                // Broadcast message to all active observers in the room
                chatRoomManager.broadcast(roomId, processedMessage);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Error in ChatStream: {}", t.getMessage());
                chatRoomManager.removeObserverGlobally(responseObserver);
            }

            @Override
            public void onCompleted() {
                log.info("ChatStream completed by client");
                chatRoomManager.removeObserverGlobally(responseObserver);
                try {
                    responseObserver.onCompleted();
                } catch (Exception ignored) {
                }
            }
        };
    }

    @Override
    public void getRoomUsers(RoomRequest request, StreamObserver<UserListResponse> responseObserver) {
        String roomId = request.getRoomId();
        log.info("Received GetRoomUsers request for room {}", roomId);

        List<UserInfo> users = chatRoomManager.getUsersInRoom(roomId);
        UserListResponse response = UserListResponse.newBuilder()
                .addAllUsers(users)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void leaveRoom(LeaveRoomRequest request, StreamObserver<LeaveRoomResponse> responseObserver) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();
        log.info("Received LeaveRoom request for room {} user {}", roomId, userId);

        String username = chatRoomManager.removeUser(roomId, userId);

        // Announce user departure with SYSTEM_LEAVE
        ChatMessage leaveMessage = ChatMessage.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setRoomId(roomId)
                .setUserId(userId)
                .setUsername(username)
                .setContent(username + " left the room")
                .setTimestamp(System.currentTimeMillis())
                .setType(MessageType.SYSTEM_LEAVE)
                .build();

        // Broadcast to remaining users in room
        chatRoomManager.broadcast(roomId, leaveMessage);

        LeaveRoomResponse response = LeaveRoomResponse.newBuilder()
                .setSuccess(true)
                .setMessage("User " + userId + " left room " + roomId)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
