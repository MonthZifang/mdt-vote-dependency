package com.mdt.vote.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VoteResult {
    private final boolean success;
    private final String sessionId;
    private final String message;
    private final Map<String, Object> data;

    private VoteResult(boolean success, String sessionId, String message, Map<String, Object> data) {
        this.success = success;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.message = message == null ? "" : message;
        this.data = data == null
            ? Collections.<String, Object>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data));
    }

    public static VoteResult success(String sessionId, String message, Map<String, Object> data) {
        return new VoteResult(true, sessionId, message, data);
    }

    public static VoteResult failure(String sessionId, String message) {
        return new VoteResult(false, sessionId, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
