package com.example.chaticalmusic.model;

public class Member {
    private String uid;
    private String displayName;
    private String photoUrl;
    private boolean isHost;
    private boolean isCoDj;
    private boolean hasRequestedAux;

    public Member(String uid, String displayName, String photoUrl, boolean isHost, boolean isCoDj, boolean hasRequestedAux) {
        this.uid = uid;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.isHost = isHost;
        this.isCoDj = isCoDj;
        this.hasRequestedAux = hasRequestedAux;
    }

    public String getUid() { return uid; }
    public String getDisplayName() { return displayName; }
    public String getPhotoUrl() { return photoUrl; }
    public boolean isHost() { return isHost; }
    public boolean isCoDj() { return isCoDj; }
    public boolean hasRequestedAux() { return hasRequestedAux; }
}
