package com.mdt.vote.service;

import arc.util.Log;
import arc.util.Strings;
import com.mdt.vote.VotePluginConfiguration;
import com.mdt.vote.api.VoteCreateRequest;
import com.mdt.vote.api.VoteDependencyApi;
import com.mdt.vote.api.VoteResult;
import com.mdt.vote.api.VoteSessionSnapshot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public final class VoteService implements VoteDependencyApi {
    private final VotePluginConfiguration configuration;
    private final ScheduledExecutorService scheduler;
    private final Object mutex = new Object();

    private VoteSession activeSession;

    public VoteService(VotePluginConfiguration configuration) {
        this.configuration = configuration;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mdt-vote-dependency-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public VoteResult startVote(VoteCreateRequest request) {
        synchronized (mutex) {
            if (request == null) {
                return VoteResult.failure("", "request is required");
            }

            if (activeSession != null && !activeSession.finished) {
                if (!request.isForceReplace()) {
                    return VoteResult.failure(activeSession.sessionId, "another vote session is already active");
                }
                finishInternal(activeSession, false, "replaced by a newer vote session");
            }

            String sessionId = normalize(request.getSessionId());
            if (sessionId.isEmpty()) {
                sessionId = "vote-" + System.currentTimeMillis();
            }

            String actionType = normalize(request.getActionType());
            if (actionType.isEmpty()) {
                return VoteResult.failure(sessionId, "actionType is required");
            }

            String title = request.getTitle() == null || request.getTitle().trim().isEmpty()
                ? "Vote: " + actionType
                : request.getTitle().trim();
            String description = request.getDescription() == null || request.getDescription().trim().isEmpty()
                ? buildDefaultDescription(actionType, request.getTarget())
                : request.getDescription().trim();

            VoteSession session = new VoteSession();
            session.sessionId = sessionId;
            session.title = title;
            session.description = description;
            session.actionType = actionType;
            session.target = request.getTarget() == null ? "" : request.getTarget().trim();
            session.actionData = new LinkedHashMap<String, String>(request.getActionData());
            session.initiatorUuid = safeTrim(request.getInitiatorUuid());
            session.initiatorName = safeTrim(request.getInitiatorName());
            session.durationSeconds = request.getDurationSeconds() > 0 ? request.getDurationSeconds() : configuration.getDefaultDurationSeconds();
            session.passRatio = request.getPassRatio() > 0d ? request.getPassRatio() : configuration.getDefaultPassRatio();
            session.minimumYesVotes = request.getMinimumYesVotes() > 0 ? request.getMinimumYesVotes() : configuration.getMinimumYesVotes();
            session.allowTargetVote = request.isAllowTargetVote() || configuration.isDefaultAllowTargetVote();
            session.startedAtMillis = System.currentTimeMillis();
            session.endAtMillis = session.startedAtMillis + session.durationSeconds * 1000L;
            session.eligibleVoters = collectEligibleVoters(session.target, session.allowTargetVote);
            if (session.eligibleVoters.isEmpty()) {
                return VoteResult.failure(sessionId, "no eligible online voters were found");
            }
            session.requiredYesCount = Math.max(session.minimumYesVotes, (int)Math.ceil(session.eligibleVoters.size() * session.passRatio));
            session.requiredYesCount = Math.min(session.requiredYesCount, session.eligibleVoters.size());

            activeSession = session;
            scheduleTick(session);
            announceSessionStarted(session);

            Map<String, Object> data = buildResultData(session);
            return VoteResult.success(session.sessionId, "vote session created", data);
        }
    }

    @Override
    public VoteResult castVote(String sessionId, String voterUuid, String voterName, boolean agree) {
        synchronized (mutex) {
            VoteSession session = activeSession;
            if (session == null || session.finished) {
                return VoteResult.failure("", "no active vote session");
            }
            if (!normalize(sessionId).isEmpty() && !session.sessionId.equalsIgnoreCase(normalize(sessionId))) {
                return VoteResult.failure(session.sessionId, "sessionId does not match current active vote");
            }

            String normalizedUuid = safeTrim(voterUuid);
            if (normalizedUuid.isEmpty()) {
                return VoteResult.failure(session.sessionId, "voterUuid is required");
            }
            if (!session.eligibleVoters.contains(normalizedUuid)) {
                return VoteResult.failure(session.sessionId, "you are not eligible to vote in this session");
            }
            if (session.votes.containsKey(normalizedUuid)) {
                return VoteResult.failure(session.sessionId, "you have already voted");
            }

            session.votes.put(normalizedUuid, Boolean.valueOf(agree));
            session.voterNames.put(normalizedUuid, safeTrim(voterName));
            broadcast("[accent][Vote][] " + safeName(voterName) + " voted " + (agree ? "[green]YES[]" : "[scarlet]NO[]") + " for [accent]" + session.title + "[]");
            renderSession(session);

            VoteDecision decision = evaluate(session);
            if (decision.resolved) {
                finishInternal(session, decision.passed, decision.reason);
            }

            Map<String, Object> data = buildResultData(session);
            return VoteResult.success(session.sessionId, "vote accepted", data);
        }
    }

    @Override
    public VoteResult cancelVote(String sessionId, String reason) {
        synchronized (mutex) {
            VoteSession session = activeSession;
            if (session == null || session.finished) {
                return VoteResult.failure("", "no active vote session");
            }
            if (!normalize(sessionId).isEmpty() && !session.sessionId.equalsIgnoreCase(normalize(sessionId))) {
                return VoteResult.failure(session.sessionId, "sessionId does not match current active vote");
            }
            finishInternal(session, false, safeTrim(reason).isEmpty() ? "cancelled by operator" : safeTrim(reason));
            return VoteResult.success(session.sessionId, "vote session cancelled", buildResultData(session));
        }
    }

    @Override
    public VoteSessionSnapshot getActiveSession() {
        synchronized (mutex) {
            return activeSession == null ? null : snapshot(activeSession);
        }
    }

    private void scheduleTick(final VoteSession session) {
        final int interval = Math.max(1, configuration.getRenderIntervalSeconds());
        session.future = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                synchronized (mutex) {
                    if (activeSession != session || session.finished) {
                        return;
                    }
                    if (System.currentTimeMillis() >= session.endAtMillis) {
                        VoteDecision decision = evaluate(session);
                        finishInternal(session, decision.passed, decision.reason.isEmpty() ? "vote ended" : decision.reason);
                        return;
                    }
                    renderSession(session);
                }
            }
        }, 0L, interval, TimeUnit.SECONDS);
    }

    private void announceSessionStarted(VoteSession session) {
        String message = "[accent][Vote Started][] " + session.title
            + "\n" + session.description
            + "\n[lightgray]Type /vote yes or /vote no[]"
            + "\n[lightgray]Need " + session.requiredYesCount + " yes votes in " + session.durationSeconds + " seconds.[]";
        broadcast(message);
        renderSession(session);
    }

    private void renderSession(VoteSession session) {
        long remaining = Math.max(0L, (session.endAtMillis - System.currentTimeMillis() + 999L) / 1000L);
        String content = session.description
            + "\n[green]YES[] " + yesCount(session) + " [scarlet]NO[] " + noCount(session)
            + "\n[lightgray]Need[] " + session.requiredYesCount + " [lightgray]Remain[] " + remaining + "s";

        if (!tryRenderHud(session.title, content)) {
            broadcast("[accent][Vote][] " + session.title + " | yes=" + yesCount(session) + " no=" + noCount(session) + " need=" + session.requiredYesCount + " remain=" + remaining + "s");
        }
    }

    private boolean tryRenderHud(String title, String content) {
        try {
            Class<?> rendererClass = Class.forName("com.mdt.renderer.UhdRendererPlugin");
            Object rendererApi = rendererClass.getMethod("getApi").invoke(null);
            if (rendererApi == null) {
                return false;
            }
            Method renderMethod = rendererApi.getClass().getMethod(
                "render",
                String.class,
                String.class,
                String.class,
                float.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                int.class,
                float.class,
                float.class
            );
            renderMethod.invoke(
                rendererApi,
                "0",
                title,
                content,
                Float.valueOf(Math.max(2, configuration.getRenderIntervalSeconds() + 1)),
                Boolean.TRUE,
                Integer.valueOf(configuration.getRenderWindowWidth()),
                Integer.valueOf(configuration.getRenderWindowHeight()),
                Integer.valueOf(configuration.getRenderPlayerScreenX()),
                Integer.valueOf(configuration.getRenderPlayerScreenY()),
                Float.valueOf(configuration.getRenderMapX()),
                Float.valueOf(configuration.getRenderMapY())
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private VoteDecision evaluate(VoteSession session) {
        int yes = yesCount(session);
        int no = noCount(session);
        int eligible = session.eligibleVoters.size();
        int remaining = eligible - yes - no;

        if (yes >= session.requiredYesCount) {
            return VoteDecision.pass("vote passed");
        }
        if (yes + remaining < session.requiredYesCount) {
            return VoteDecision.fail("vote can no longer reach the required yes count");
        }
        if (System.currentTimeMillis() >= session.endAtMillis) {
            return VoteDecision.fail("vote time expired");
        }
        return VoteDecision.pending();
    }

    private void finishInternal(VoteSession session, boolean passed, String reason) {
        if (session.finished) {
            return;
        }
        session.finished = true;
        if (session.future != null) {
            session.future.cancel(false);
            session.future = null;
        }

        String safeReason = safeTrim(reason);
        if (passed) {
            VoteResult actionResult = executeAction(session);
            broadcast("[accent][Vote Passed][] " + session.title + (safeReason.isEmpty() ? "" : "\n[lightgray]" + safeReason + "[]"));
            if (!actionResult.isSuccess()) {
                broadcast("[scarlet][Vote Action Failed][] " + actionResult.getMessage());
                Log.warn("Vote action failed. session=@ message=@", session.sessionId, actionResult.getMessage());
            }
        } else {
            broadcast("[scarlet][Vote Failed][] " + session.title + (safeReason.isEmpty() ? "" : "\n[lightgray]" + safeReason + "[]"));
        }

        renderFinalResult(session, passed, safeReason);
        if (activeSession == session) {
            activeSession = null;
        }
    }

    private void renderFinalResult(VoteSession session, boolean passed, String reason) {
        String title = passed ? "Vote Passed" : "Vote Failed";
        String content = session.title
            + "\n[green]YES[] " + yesCount(session) + " [scarlet]NO[] " + noCount(session)
            + (reason.isEmpty() ? "" : "\n[lightgray]" + reason + "[]");
        tryRenderHud(title, content);
    }

    private VoteResult executeAction(VoteSession session) {
        if ("kick-player".equals(session.actionType)) {
            return executeKick(session);
        }
        if ("spectate-player".equals(session.actionType)) {
            return executeSpectate(session);
        }
        if ("redirect-player".equals(session.actionType)) {
            return executeRedirect(session, false);
        }
        if ("redirect-all".equals(session.actionType)) {
            return executeRedirect(session, true);
        }
        if ("custom-message".equals(session.actionType)) {
            return executeCustomMessage(session);
        }
        if ("world-operation".equals(session.actionType)) {
            return executeWorldOperation(session);
        }
        return VoteResult.failure(session.sessionId, "unsupported actionType: " + session.actionType);
    }

    private VoteResult executeKick(VoteSession session) {
        Player player = resolvePlayer(session.target);
        if (player == null || player.con == null) {
            return VoteResult.failure(session.sessionId, "target player not found for kick");
        }
        String reason = firstNonEmpty(session.actionData.get("reason"), "vote passed");
        long duration = parseLong(firstNonEmpty(session.actionData.get("duration"), "60000"), 60000L);
        player.con.kick(reason, duration);
        return VoteResult.success(session.sessionId, "kick executed", Collections.<String, Object>singletonMap("target", player.plainName()));
    }

    private VoteResult executeSpectate(VoteSession session) {
        Player player = resolvePlayer(session.target);
        if (player == null) {
            return VoteResult.failure(session.sessionId, "target player not found for spectate");
        }
        player.team(Team.derelict);
        player.clearUnit();
        player.sendMessage("[accent][Vote][] You have been forced into spectate.");
        return VoteResult.success(session.sessionId, "spectate executed", Collections.<String, Object>singletonMap("target", player.plainName()));
    }

    private VoteResult executeRedirect(VoteSession session, boolean redirectAll) {
        List<Player> targets = new ArrayList<Player>();
        if (redirectAll) {
            for (Player player : Groups.player) {
                if (player != null) {
                    targets.add(player);
                }
            }
        } else {
            Player player = resolvePlayer(session.target);
            if (player != null) {
                targets.add(player);
            }
        }

        if (targets.isEmpty()) {
            return VoteResult.failure(session.sessionId, "no redirect target players found");
        }

        String host = firstNonEmpty(session.actionData.get("host"), configuration.getRedirectHost());
        int port = parseInt(firstNonEmpty(session.actionData.get("port"), String.valueOf(configuration.getRedirectPort())), configuration.getRedirectPort());
        String name = firstNonEmpty(session.actionData.get("name"), configuration.getRedirectName());
        String uri = String.format(configuration.getRedirectUriTemplate(), host, Integer.valueOf(port), name);

        for (Player player : targets) {
            if (configuration.isRedirectSendChat()) {
                player.sendMessage(String.format(configuration.getRedirectChatTemplate(), uri));
            }
            if (configuration.isRedirectSendInfo() && player.con != null) {
                Call.infoMessage(player.con, String.format(configuration.getRedirectInfoTemplate(), uri));
            }
            if (configuration.isRedirectKick() && player.con != null) {
                player.con.kick(String.format(configuration.getRedirectKickTemplate(), uri), configuration.getRedirectKickDuration());
            }
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("count", Integer.valueOf(targets.size()));
        data.put("uri", uri);
        return VoteResult.success(session.sessionId, "redirect executed", data);
    }

    private VoteResult executeCustomMessage(VoteSession session) {
        String message = firstNonEmpty(session.actionData.get("message"), session.description);
        String target = normalize(session.target);
        if (target.isEmpty() || "all".equals(target) || "0".equals(target)) {
            broadcast("[accent][Vote Message][] " + message);
            return VoteResult.success(session.sessionId, "custom message broadcast", Collections.<String, Object>singletonMap("scope", "all"));
        }

        Player player = resolvePlayer(session.target);
        if (player == null) {
            return VoteResult.failure(session.sessionId, "target player not found for custom message");
        }
        player.sendMessage("[accent][Vote Message][] " + message);
        return VoteResult.success(session.sessionId, "custom message sent", Collections.<String, Object>singletonMap("target", player.plainName()));
    }

    private VoteResult executeWorldOperation(VoteSession session) {
        try {
            String operation = firstNonEmpty(session.actionData.get("operation"), "");
            if (operation.isEmpty()) {
                return VoteResult.failure(session.sessionId, "world-operation requires actionData.operation");
            }

            Class<?> pluginClass = Class.forName("com.mdt.foundation.FoundationWorldControlPlugin");
            Object api = pluginClass.getMethod("getApi").invoke(null);
            if (api == null) {
                return VoteResult.failure(session.sessionId, "foundation world control API is not available");
            }

            Class<?> requestClass = Class.forName("com.mdt.foundation.api.ActionRequest");
            Method ofMethod = requestClass.getMethod("of", String.class, Map.class);
            Map<String, String> parameters = new LinkedHashMap<String, String>(session.actionData);
            parameters.remove("operation");
            Object request = ofMethod.invoke(null, operation, parameters);
            Object result = api.getClass().getMethod("execute", requestClass).invoke(api, request);
            boolean success = Boolean.TRUE.equals(result.getClass().getMethod("isSuccess").invoke(result));
            String message = String.valueOf(result.getClass().getMethod("getMessage").invoke(result));
            return success
                ? VoteResult.success(session.sessionId, "world operation executed: " + message, null)
                : VoteResult.failure(session.sessionId, "world operation failed: " + message);
        } catch (Throwable throwable) {
            Log.err(throwable);
            return VoteResult.failure(session.sessionId, "world operation invoke failed: " + throwable.getMessage());
        }
    }

    private Set<String> collectEligibleVoters(String targetSelector, boolean allowTargetVote) {
        LinkedHashSet<String> voters = new LinkedHashSet<String>();
        String targetUuid = allowTargetVote ? "" : resolveTargetUuid(targetSelector);
        for (Player player : Groups.player) {
            if (player == null) {
                continue;
            }
            String uuid = safeTrim(player.uuid());
            if (uuid.isEmpty()) {
                continue;
            }
            if (!targetUuid.isEmpty() && targetUuid.equalsIgnoreCase(uuid)) {
                continue;
            }
            voters.add(uuid);
        }
        return voters;
    }

    private VoteSessionSnapshot snapshot(VoteSession session) {
        long remaining = Math.max(0L, (session.endAtMillis - System.currentTimeMillis() + 999L) / 1000L);
        return new VoteSessionSnapshot(
            session.sessionId,
            session.title,
            session.description,
            session.actionType,
            session.target,
            session.eligibleVoters.size(),
            yesCount(session),
            noCount(session),
            session.requiredYesCount,
            remaining,
            session.finished
        );
    }

    private Map<String, Object> buildResultData(VoteSession session) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        VoteSessionSnapshot snapshot = snapshot(session);
        data.put("title", snapshot.getTitle());
        data.put("actionType", snapshot.getActionType());
        data.put("target", snapshot.getTarget());
        data.put("eligibleCount", Integer.valueOf(snapshot.getEligibleCount()));
        data.put("yesCount", Integer.valueOf(snapshot.getYesCount()));
        data.put("noCount", Integer.valueOf(snapshot.getNoCount()));
        data.put("requiredYesCount", Integer.valueOf(snapshot.getRequiredYesCount()));
        data.put("remainingSeconds", Long.valueOf(snapshot.getRemainingSeconds()));
        return data;
    }

    private int yesCount(VoteSession session) {
        int count = 0;
        for (Boolean vote : session.votes.values()) {
            if (Boolean.TRUE.equals(vote)) {
                count++;
            }
        }
        return count;
    }

    private int noCount(VoteSession session) {
        int count = 0;
        for (Boolean vote : session.votes.values()) {
            if (Boolean.FALSE.equals(vote)) {
                count++;
            }
        }
        return count;
    }

    private String buildDefaultDescription(String actionType, String target) {
        if (safeTrim(target).isEmpty()) {
            return "Action: " + actionType;
        }
        return "Action: " + actionType + " | Target: " + target;
    }

    private Player resolvePlayer(String selector) {
        String normalized = normalize(selector);
        if (normalized.isEmpty() || "all".equals(normalized) || "0".equals(normalized)) {
            return null;
        }

        for (Player player : Groups.player) {
            if (player == null) {
                continue;
            }
            if (normalized.equalsIgnoreCase(safeTrim(player.uuid()))
                || normalized.equalsIgnoreCase(normalize(player.plainName()))
                || normalized.equalsIgnoreCase(normalize(Strings.stripColors(player.name)))) {
                return player;
            }
        }

        String uuid = resolveUuidByComId(normalized);
        if (!uuid.isEmpty()) {
            for (Player player : Groups.player) {
                if (player != null && uuid.equalsIgnoreCase(safeTrim(player.uuid()))) {
                    return player;
                }
            }
        }
        return null;
    }

    private String resolveTargetUuid(String selector) {
        Player player = resolvePlayer(selector);
        if (player != null) {
            return safeTrim(player.uuid());
        }
        String normalized = normalize(selector);
        if (normalized.isEmpty()) {
            return "";
        }
        return resolveUuidByComId(normalized);
    }

    private String resolveUuidByComId(String comId) {
        try {
            Class<?> pluginClass = Class.forName("com.mdt.jump.JumpComIdPlugin");
            Object api = pluginClass.getMethod("getApi").invoke(null);
            if (api == null) {
                return "";
            }
            Object optional = api.getClass().getMethod("findByComId", String.class).invoke(api, comId);
            Boolean present = (Boolean)optional.getClass().getMethod("isPresent").invoke(optional);
            if (!Boolean.TRUE.equals(present)) {
                return "";
            }
            Object record = optional.getClass().getMethod("get").invoke(optional);
            Object uuid = record.getClass().getMethod("getUuid").invoke(record);
            return uuid == null ? "" : uuid.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void broadcast(String message) {
        for (Player player : Groups.player) {
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeName(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? "unknown" : trimmed;
    }

    private String firstNonEmpty(String value, String fallback) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class VoteDecision {
        private final boolean resolved;
        private final boolean passed;
        private final String reason;

        private VoteDecision(boolean resolved, boolean passed, String reason) {
            this.resolved = resolved;
            this.passed = passed;
            this.reason = reason == null ? "" : reason;
        }

        private static VoteDecision pending() {
            return new VoteDecision(false, false, "");
        }

        private static VoteDecision pass(String reason) {
            return new VoteDecision(true, true, reason);
        }

        private static VoteDecision fail(String reason) {
            return new VoteDecision(true, false, reason);
        }
    }

    private static final class VoteSession {
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
        private Set<String> eligibleVoters = new LinkedHashSet<String>();
        private int requiredYesCount;
        private long startedAtMillis;
        private long endAtMillis;
        private boolean finished;
        private Map<String, Boolean> votes = new LinkedHashMap<String, Boolean>();
        private Map<String, String> voterNames = new LinkedHashMap<String, String>();
        private java.util.concurrent.ScheduledFuture<?> future;
    }
}
