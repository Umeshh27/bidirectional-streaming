package com.example.grpc.chat.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateRoomRequest {

    private String roomId;

    @NotBlank(message = "roomName is required")
    private String roomName;

    private String description;

    public CreateRoomRequest() {
    }

    public CreateRoomRequest(String roomName, String description) {
        this.roomName = roomName;
        this.description = description;
    }

    public CreateRoomRequest(String roomId, String roomName, String description) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.description = description;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
