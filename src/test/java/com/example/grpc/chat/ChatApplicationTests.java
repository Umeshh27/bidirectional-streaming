package com.example.grpc.chat;

import com.example.grpc.chat.controller.RoomRestController;
import com.example.grpc.chat.dto.CreateRoomRequest;
import com.example.grpc.chat.dto.MessageHistoryDto;
import com.example.grpc.chat.dto.RoomResponse;
import com.example.grpc.chat.dto.RoomSummaryDto;
import com.example.grpc.chat.model.ChatMessageEntity;
import com.example.grpc.chat.repository.ChatMessageRepository;
import com.example.grpc.chat.service.ChatGrpcService;
import com.example.grpc.chat.service.ChatRoomManager;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChatApplicationTests {

    @Autowired
    private RoomRestController roomRestController;

    @Autowired
    private ChatGrpcService chatGrpcService;

    @Autowired
    private ChatRoomManager chatRoomManager;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertNotNull(roomRestController);
        assertNotNull(chatGrpcService);
        assertNotNull(chatRoomManager);
        assertNotNull(chatMessageRepository);
    }

    @Test
    void testCreateRoomRestEndpoint() {
        CreateRoomRequest request = new CreateRoomRequest("general", "General discussion room");
        ResponseEntity<RoomResponse> response = roomRestController.createRoom(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("general", response.getBody().getRoomId());
        assertEquals("general", response.getBody().getRoomName());
        assertEquals("General discussion room", response.getBody().getDescription());
    }

    @Test
    void testListRoomsRestEndpoint() {
        CreateRoomRequest request = new CreateRoomRequest("general", "General discussion room");
        roomRestController.createRoom(request);

        ResponseEntity<List<RoomSummaryDto>> response = roomRestController.listRooms();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());

        RoomSummaryDto room = response.getBody().stream()
                .filter(r -> "general".equals(r.getRoomId()))
                .findFirst()
                .orElse(null);

        assertNotNull(room);
        assertEquals("general", room.getRoomName());
        assertEquals(0, room.getUserCount());
    }

    @Test
    void testGrpcJoinRoomEmitsSystemJoin() throws InterruptedException {
        String roomId = "general";
        String userId = "user-A";
        String username = "Alice";

        List<ChatMessage> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<ChatMessage> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage value) {
                received.add(value);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {}
        };

        JoinRoomRequest request = JoinRoomRequest.newBuilder()
                .setRoomId(roomId)
                .setUserId(userId)
                .setUsername(username)
                .build();

        chatGrpcService.joinRoom(request, responseObserver);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Client should receive SYSTEM_JOIN message");
        assertEquals(1, received.size());
        assertEquals(MessageType.SYSTEM_JOIN, received.get(0).getType());
        assertEquals(userId, received.get(0).getUserId());
        assertEquals(username, received.get(0).getUsername());
        assertTrue(received.get(0).getContent().contains("Alice joined the room"));
    }

    @Test
    void testChatStreamBroadcastAndPersistence() throws InterruptedException {
        String roomId = "general";

        // Client A joins
        List<ChatMessage> clientAReceived = new CopyOnWriteArrayList<>();
        CountDownLatch clientALatch = new CountDownLatch(1);
        chatGrpcService.joinRoom(
                JoinRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-A").setUsername("Alice").build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {
                        clientAReceived.add(value);
                        clientALatch.countDown();
                    }
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                }
        );
        assertTrue(clientALatch.await(3, TimeUnit.SECONDS));

        // Client B joins
        List<ChatMessage> clientBReceived = new CopyOnWriteArrayList<>();
        CountDownLatch clientBLatch = new CountDownLatch(1);
        chatGrpcService.joinRoom(
                JoinRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-B").setUsername("Bob").build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {
                        clientBReceived.add(value);
                        clientBLatch.countDown();
                    }
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                }
        );
        assertTrue(clientBLatch.await(3, TimeUnit.SECONDS));

        // Client A should have received SYSTEM_JOIN for Client B as well
        assertEquals(2, clientAReceived.size());
        assertEquals("user-B", clientAReceived.get(1).getUserId());
        assertEquals(MessageType.SYSTEM_JOIN, clientAReceived.get(1).getType());

        // Now Client A sends "Hello World" via ChatStream
        StreamObserver<ChatMessage> chatStreamResponseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage value) {}
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        };

        StreamObserver<ChatMessage> requestObserver = chatGrpcService.chatStream(chatStreamResponseObserver);

        String msgId = UUID.randomUUID().toString();
        ChatMessage msg = ChatMessage.newBuilder()
                .setMessageId(msgId)
                .setRoomId(roomId)
                .setUserId("user-A")
                .setUsername("Alice")
                .setContent("Hello World")
                .setType(MessageType.TEXT)
                .setTimestamp(System.currentTimeMillis())
                .build();

        requestObserver.onNext(msg);

        // Allow broadcast and async persistence
        Thread.sleep(500);

        // Verify both clients received the broadcast
        assertTrue(clientAReceived.stream().anyMatch(m -> "Hello World".equals(m.getContent())));
        assertTrue(clientBReceived.stream().anyMatch(m -> "Hello World".equals(m.getContent())));

        // Verify database persistence (Requirement 10)
        List<ChatMessageEntity> savedEntities = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId);
        assertEquals(1, savedEntities.size());
        ChatMessageEntity entity = savedEntities.get(0);
        assertEquals(msgId, entity.getMessageId());
        assertEquals(roomId, entity.getRoomId());
        assertEquals("user-A", entity.getUserId());
        assertEquals("Alice", entity.getUsername());
        assertEquals("Hello World", entity.getContent());
        assertEquals("TEXT", entity.getMessageType());
        assertNotNull(entity.getTimestamp());

        // Verify history endpoint (Requirement 11)
        ResponseEntity<List<MessageHistoryDto>> historyResponse = roomRestController.getMessageHistory(roomId);
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        assertNotNull(historyResponse.getBody());
        assertEquals(1, historyResponse.getBody().size());
        assertEquals("Hello World", historyResponse.getBody().get(0).getContent());
        assertEquals("TEXT", historyResponse.getBody().get(0).getType());
    }

    @Test
    void testGetRoomUsersAndLeaveRoom() throws InterruptedException {
        String roomId = "general";

        // Alice joins
        chatGrpcService.joinRoom(
                JoinRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-A").setUsername("Alice").build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {}
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                }
        );

        // Bob joins
        List<ChatMessage> bobReceived = new CopyOnWriteArrayList<>();
        chatGrpcService.joinRoom(
                JoinRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-B").setUsername("Bob").build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {
                        bobReceived.add(value);
                    }
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                }
        );

        // Requirement 8: GetRoomUsers
        CountDownLatch usersLatch = new CountDownLatch(1);
        final List<UserInfo> userList = new ArrayList<>();
        chatGrpcService.getRoomUsers(RoomRequest.newBuilder().setRoomId(roomId).build(), new StreamObserver<>() {
            @Override
            public void onNext(UserListResponse value) {
                userList.addAll(value.getUsersList());
                usersLatch.countDown();
            }
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        });

        assertTrue(usersLatch.await(3, TimeUnit.SECONDS));
        assertEquals(2, userList.size());
        assertTrue(userList.stream().anyMatch(u -> "user-A".equals(u.getUserId())));
        assertTrue(userList.stream().anyMatch(u -> "user-B".equals(u.getUserId())));

        // Alice is listening to receive SYSTEM_LEAVE when Bob leaves
        List<ChatMessage> aliceReceived = new CopyOnWriteArrayList<>();
        chatGrpcService.joinRoom(
                JoinRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-A-listener").setUsername("AliceListener").build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(ChatMessage value) {
                        aliceReceived.add(value);
                    }
                    @Override
                    public void onError(Throwable t) {}
                    @Override
                    public void onCompleted() {}
                }
        );

        // Requirement 9: Bob leaves room
        CountDownLatch leaveLatch = new CountDownLatch(1);
        chatGrpcService.leaveRoom(LeaveRoomRequest.newBuilder().setRoomId(roomId).setUserId("user-B").build(), new StreamObserver<>() {
            @Override
            public void onNext(LeaveRoomResponse value) {
                assertTrue(value.getSuccess());
                leaveLatch.countDown();
            }
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        });

        assertTrue(leaveLatch.await(3, TimeUnit.SECONDS));

        // Check that Alice received SYSTEM_LEAVE
        assertTrue(aliceReceived.stream().anyMatch(m -> m.getType() == MessageType.SYSTEM_LEAVE && "user-B".equals(m.getUserId())));

        // Check that only Alice remains
        CountDownLatch remainingLatch = new CountDownLatch(1);
        final List<UserInfo> remainingUsers = new ArrayList<>();
        chatGrpcService.getRoomUsers(RoomRequest.newBuilder().setRoomId(roomId).build(), new StreamObserver<>() {
            @Override
            public void onNext(UserListResponse value) {
                remainingUsers.addAll(value.getUsersList());
                remainingLatch.countDown();
            }
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {}
        });

        assertTrue(remainingLatch.await(3, TimeUnit.SECONDS));
        assertFalse(remainingUsers.stream().anyMatch(u -> "user-B".equals(u.getUserId())));
    }
}
