package com.mdt.vote.api;

public interface VoteDependencyApi {
    VoteResult startVote(VoteCreateRequest request);

    VoteResult castVote(String sessionId, String voterUuid, String voterName, boolean agree);

    VoteResult cancelVote(String sessionId, String reason);

    VoteSessionSnapshot getActiveSession();
}
