package com.mdt.vote;

import arc.util.CommandHandler;
import arc.util.Log;
import com.mdt.vote.api.VoteCreateRequest;
import com.mdt.vote.api.VoteDependencyApi;
import com.mdt.vote.api.VoteResult;
import com.mdt.vote.api.VoteSessionSnapshot;
import com.mdt.vote.service.VoteService;
import java.nio.file.Path;
import java.nio.file.Paths;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class VoteDependencyPlugin extends Plugin {
    private static final Path DATA_DIRECTORY = Paths.get("config", "mods", "config", "mdt-vote-dependency");

    private static volatile VoteDependencyApi api;

    private VotePluginConfiguration configuration;
    private VoteService service;

    public static VoteDependencyApi getApi() {
        return api;
    }

    @Override
    public void init() {
        try {
            configuration = VotePluginConfiguration.load(DATA_DIRECTORY);
            service = new VoteService(configuration);
            api = service;
            Log.info("MDT Vote Dependency loaded. config=@", configuration.getConfigFile());
        } catch (Exception exception) {
            throw new RuntimeException("MDT Vote Dependency init failed.", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("vote-dep-status", "Show active vote session status.", args -> {
            VoteSessionSnapshot session = service.getActiveSession();
            if (session == null) {
                Log.info("No active vote session. config=@", configuration.getConfigFile());
                return;
            }
            Log.info(
                "session=@ action=@ target=@ yes=@ no=@ need=@ remain=@s",
                session.getSessionId(),
                session.getActionType(),
                session.getTarget(),
                session.getYesCount(),
                session.getNoCount(),
                session.getRequiredYesCount(),
                session.getRemainingSeconds()
            );
        });

        handler.register("vote-dep-start", "<actionType> <target> <title...>", "Create a vote session for quick testing.", args -> {
            String title = join(args, 2);
            VoteCreateRequest request = new VoteCreateRequest()
                .setActionType(args[0])
                .setTarget(args[1])
                .setTitle(title.isEmpty() ? "Vote: " + args[0] : title)
                .setDescription(title.isEmpty() ? "Target: " + args[1] : title)
                .setInitiatorUuid("console")
                .setInitiatorName("console");
            VoteResult result = service.startVote(request);
            Log.info("success=@ session=@ message=@", result.isSuccess(), result.getSessionId(), result.getMessage());
        });

        handler.register("vote-dep-cancel", "[reason...]", "Cancel current vote session.", args -> {
            VoteSessionSnapshot session = service.getActiveSession();
            VoteResult result = service.cancelVote(session == null ? "" : session.getSessionId(), join(args, 0));
            Log.info("success=@ message=@", result.isSuccess(), result.getMessage());
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("vote", "<yes/no> [sessionId]", "Cast your vote.", (args, player) -> {
            boolean agree;
            if ("yes".equalsIgnoreCase(args[0]) || "y".equalsIgnoreCase(args[0])) {
                agree = true;
            } else if ("no".equalsIgnoreCase(args[0]) || "n".equalsIgnoreCase(args[0])) {
                agree = false;
            } else {
                player.sendMessage("[scarlet]Usage: /vote yes|no [sessionId][]");
                return;
            }
            String sessionId = args.length >= 2 ? args[1] : "";
            VoteResult result = service.castVote(sessionId, player.uuid(), player.plainName(), agree);
            player.sendMessage((result.isSuccess() ? "[accent]" : "[scarlet]") + result.getMessage() + "[]");
        });

        handler.<Player>register("votestatus", "Show current vote status.", (args, player) -> {
            VoteSessionSnapshot session = service.getActiveSession();
            if (session == null) {
                player.sendMessage("[scarlet]No active vote session.[]");
                return;
            }
            player.sendMessage(
                "[accent]" + session.getTitle() + "[]\n"
                    + session.getDescription() + "\n"
                    + "yes=" + session.getYesCount()
                    + " no=" + session.getNoCount()
                    + " need=" + session.getRequiredYesCount()
                    + " remain=" + session.getRemainingSeconds() + "s"
            );
        });
    }

    private String join(String[] args, int start) {
        if (args == null || start >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString().trim();
    }
}
