package com.example.grpc.chat.repository;

import com.example.grpc.chat.model.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, String> {

    Optional<ChatRoomEntity> findByRoomName(String roomName);
}
