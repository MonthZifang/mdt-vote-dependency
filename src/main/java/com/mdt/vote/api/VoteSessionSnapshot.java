package com.mdt.vote.api;

public final class VoteSessionSnapshot {
    private final String sessionId;
    private final String title;
    private final String description;
    private final String actionType;
    private final String target;
    private final int eligibleCount;
    private final int yesCount;
    private final int noCount;
    private final int requiredYesCount;
    private final long remainingSeconds;
    private final boolean finished;

    public VoteSessionSnapshot(
        String sessionId,
        String title,
        String description,
        String actionType,
        String target,
        int eligibleCount,
        int yesCount,
        int noCount,
        int requiredYesCount,
        long remainingSeconds,
        boolean finished
    ) {
        this.sessionId = sessionId;
        this.title = title;
        this.description = description;
        this.actionType = actionType;
        this.target = target;
        this.eligibleCount = eligibleCount;
        this.yesCount = yesCount;
        this.noCount = noCount;
        this.requiredYesCount = requiredYesCount;
        this.remainingSeconds = remainingSeconds;
        this.finished = finished;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTarget() {
        return target;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }

    public int getYesCount() {
        return yesCount;
    }

    public int getNoCount() {
        return noCount;
    }

    public int getRequiredYesCount() {
        return requiredYesCount;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public boolean isFinished() {
        return finished;
    }
}
