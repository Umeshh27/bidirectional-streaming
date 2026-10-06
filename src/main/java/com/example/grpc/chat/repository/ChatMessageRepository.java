package com.example.grpc.chat.repository;

import com.example.grpc.chat.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findTop100ByRoomIdOrderByTimestampDescIdDesc(String roomId);

    List<ChatMessageEntity> findByRoomIdOrderByTimestampDesc(String roomId);
}
