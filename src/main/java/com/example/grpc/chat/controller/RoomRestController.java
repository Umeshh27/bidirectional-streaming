package com.example.grpc.chat.controller;

import com.example.grpc.chat.dto.CreateRoomRequest;
import com.example.grpc.chat.dto.MessageHistoryDto;
import com.example.grpc.chat.dto.RoomResponse;
import com.example.grpc.chat.dto.RoomSummaryDto;
import com.example.grpc.chat.model.ChatMessageEntity;
import com.example.grpc.chat.model.ChatRoomEntity;
import com.example.grpc.chat.repository.ChatMessageRepository;
import com.example.grpc.chat.service.ChatRoomManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomRestController {

    private final ChatRoomManager chatRoomManager;
    private final ChatMessageRepository chatMessageRepository;

    public RoomRestController(ChatRoomManager chatRoomManager, ChatMessageRepository chatMessageRepository) {
        this.chatRoomManager = chatRoomManager;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Requirement 4: Create new chat room
     */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        ChatRoomEntity room = chatRoomManager.createOrGetRoom(
                request.getRoomId(),
                request.getRoomName(),
                request.getDescription()
        );

        RoomResponse response = new RoomResponse(
                room.getRoomId(),
                room.getRoomName(),
                room.getDescription()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Requirement 5: List all active chat rooms
     */
    @GetMapping
    public ResponseEntity<List<RoomSummaryDto>> listRooms() {
        List<ChatRoomEntity> rooms = chatRoomManager.getAllRooms();
        List<RoomSummaryDto> responseList = new ArrayList<>();

        for (ChatRoomEntity room : rooms) {
            int userCount = chatRoomManager.getUserCount(room.getRoomId());
            responseList.add(new RoomSummaryDto(
                    room.getRoomId(),
                    room.getRoomName(),
                    userCount
            ));
        }

        return ResponseEntity.ok(responseList);
    }

    /**
     * Requirement 11: Retrieve last 100 messages for a room in reverse chronological order
     */
    @GetMapping("/{roomId}/messages/history")
    public ResponseEntity<List<MessageHistoryDto>> getMessageHistory(@PathVariable("roomId") String roomId) {
        List<ChatMessageEntity> entities = chatMessageRepository.findTop100ByRoomIdOrderByTimestampDescIdDesc(roomId);
        List<MessageHistoryDto> history = new ArrayList<>();

        for (ChatMessageEntity entity : entities) {
            history.add(new MessageHistoryDto(
                    entity.getMessageId(),
                    entity.getRoomId(),
                    entity.getUserId(),
                    entity.getUsername(),
                    entity.getContent(),
                    entity.getTimestamp().toString(),
                    entity.getMessageType()
            ));
        }

        return ResponseEntity.ok(history);
    }
}
