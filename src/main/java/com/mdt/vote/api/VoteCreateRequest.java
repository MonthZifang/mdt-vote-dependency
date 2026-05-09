package com.mdt.vote.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VoteCreateRequest {
    private String sessionId;
    private String title;
    private String description;
    private String initiatorUuid;
    private String initiatorName;
    private String actionType;
    private String target;
    private Map<String, String> actionData = new LinkedHashMap<String, String>();
    private int durationSeconds;
    private double passRatio;
    private int minimumYesVotes;
    private boolean allowTargetVote;
    private boolean forceReplace;

    public String getSessionId() {
        return sessionId;
    }

    public VoteCreateRequest setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public VoteCreateRequest setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public VoteCreateRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getInitiatorUuid() {
        return initiatorUuid;
    }

    public VoteCreateRequest setInitiatorUuid(String initiatorUuid) {
        this.initiatorUuid = initiatorUuid;
        return this;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public VoteCreateRequest setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
        return this;
    }

    public String getActionType() {
        return actionType;
    }

    public VoteCreateRequest setActionType(String actionType) {
        this.actionType = actionType;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public VoteCreateRequest setTarget(String target) {
        this.target = target;
        return this;
    }

    public Map<String, String> getActionData() {
        return Collections.unmodifiableMap(actionData);
    }

    public VoteCreateRequest setActionData(Map<String, String> actionData) {
        this.actionData = actionData == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(actionData);
        return this;
    }

    public VoteCreateRequest putActionData(String key, String value) {
        this.actionData.put(key, value);
        return this;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public VoteCreateRequest setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
        return this;
    }

    public double getPassRatio() {
        return passRatio;
    }

    public VoteCreateRequest setPassRatio(double passRatio) {
        this.passRatio = passRatio;
        return this;
    }

    public int getMinimumYesVotes() {
        return minimumYesVotes;
    }

    public VoteCreateRequest setMinimumYesVotes(int minimumYesVotes) {
        this.minimumYesVotes = minimumYesVotes;
        return this;
    }

    public boolean isAllowTargetVote() {
        return allowTargetVote;
    }

    public VoteCreateRequest setAllowTargetVote(boolean allowTargetVote) {
        this.allowTargetVote = allowTargetVote;
        return this;
    }

    public boolean isForceReplace() {
        return forceReplace;
    }

    public VoteCreateRequest setForceReplace(boolean forceReplace) {
        this.forceReplace = forceReplace;
        return this;
    }
}
