package com.example.chaticalmusic.model;

public class PublicRoomItem {
    private String roomId;
    private String roomName;
    private String currentTrackTitle;
    private int memberCount;

    public PublicRoomItem(String roomId, String roomName, String currentTrackTitle, int memberCount) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.currentTrackTitle = currentTrackTitle;
        this.memberCount = memberCount;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public void setCurrentTrackTitle(String currentTrackTitle) {
        this.currentTrackTitle = currentTrackTitle;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
