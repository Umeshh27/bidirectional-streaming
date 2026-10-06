package com.example.grpc.chat.dto;

public class RoomSummaryDto {

    private String roomId;
    private String roomName;
    private int userCount;

    public RoomSummaryDto() {
    }

    public RoomSummaryDto(String roomId, String roomName, int userCount) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.userCount = userCount;
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

    public int getUserCount() {
        return userCount;
    }

    public void setUserCount(int userCount) {
        this.userCount = userCount;
    }
}
