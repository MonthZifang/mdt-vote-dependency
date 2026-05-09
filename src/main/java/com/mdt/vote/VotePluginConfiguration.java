package com.mdt.vote;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class VotePluginConfiguration {
    private static final String CONFIG_FILE_NAME = "vote-dependency.properties";

    private final Path configDirectory;
    private final Path configFile;
    private final int defaultDurationSeconds;
    private final double defaultPassRatio;
    private final int minimumYesVotes;
    private final int renderIntervalSeconds;
    private final boolean defaultAllowTargetVote;
    private final int renderWindowWidth;
    private final int renderWindowHeight;
    private final int renderPlayerScreenX;
    private final int renderPlayerScreenY;
    private final float renderMapX;
    private final float renderMapY;
    private final String redirectHost;
    private final int redirectPort;
    private final String redirectName;
    private final String redirectUriTemplate;
    private final boolean redirectSendChat;
    private final boolean redirectSendInfo;
    private final boolean redirectKick;
    private final long redirectKickDuration;
    private final String redirectChatTemplate;
    private final String redirectInfoTemplate;
    private final String redirectKickTemplate;

    private VotePluginConfiguration(Path configDirectory, Properties properties) {
        this.configDirectory = configDirectory;
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        this.defaultDurationSeconds = readInt(properties, "vote.defaultDurationSeconds", 30);
        this.defaultPassRatio = readDouble(properties, "vote.defaultPassRatio", 0.6d);
        this.minimumYesVotes = readInt(properties, "vote.minimumYesVotes", 2);
        this.renderIntervalSeconds = readInt(properties, "vote.renderIntervalSeconds", 3);
        this.defaultAllowTargetVote = readBoolean(properties, "vote.defaultAllowTargetVote", false);
        this.renderWindowWidth = readInt(properties, "render.windowWidth", 460);
        this.renderWindowHeight = readInt(properties, "render.windowHeight", 220);
        this.renderPlayerScreenX = readInt(properties, "render.playerScreenX", 0);
        this.renderPlayerScreenY = readInt(properties, "render.playerScreenY", 0);
        this.renderMapX = readFloat(properties, "render.mapX", 0f);
        this.renderMapY = readFloat(properties, "render.mapY", 0f);
        this.redirectHost = read(properties, "redirect.host", "127.0.0.1");
        this.redirectPort = readInt(properties, "redirect.port", 6567);
        this.redirectName = read(properties, "redirect.name", "Target Server");
        this.redirectUriTemplate = read(properties, "redirect.uriTemplate", "mdt://jump?host=%s&port=%s&name=%s");
        this.redirectSendChat = readBoolean(properties, "redirect.sendChat", true);
        this.redirectSendInfo = readBoolean(properties, "redirect.sendInfo", true);
        this.redirectKick = readBoolean(properties, "redirect.kick", false);
        this.redirectKickDuration = readLong(properties, "redirect.kickDuration", 60000L);
        this.redirectChatTemplate = read(properties, "redirect.chatTemplate", "[accent][Vote][] Please jump to: %s");
        this.redirectInfoTemplate = read(properties, "redirect.infoTemplate", "[accent][Vote Passed][] Jump target: %s");
        this.redirectKickTemplate = read(properties, "redirect.kickTemplate", "%s");
    }

    public static VotePluginConfiguration load(Path configDirectory) throws IOException {
        Files.createDirectories(configDirectory);
        Path configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            try (InputStream inputStream = VotePluginConfiguration.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (inputStream == null) {
                    throw new IOException("Missing default resource: " + CONFIG_FILE_NAME);
                }
                Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new VotePluginConfiguration(configDirectory, properties);
    }

    private static String read(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(read(properties, key, String.valueOf(fallback)));
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(read(properties, key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long readLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(read(properties, key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float readFloat(Properties properties, String key, float fallback) {
        try {
            return Float.parseFloat(read(properties, key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double readDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(read(properties, key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public Path getConfigFile() {
        return configFile;
    }

    public int getDefaultDurationSeconds() {
        return defaultDurationSeconds;
    }

    public double getDefaultPassRatio() {
        return defaultPassRatio;
    }

    public int getMinimumYesVotes() {
        return minimumYesVotes;
    }

    public int getRenderIntervalSeconds() {
        return renderIntervalSeconds;
    }

    public boolean isDefaultAllowTargetVote() {
        return defaultAllowTargetVote;
    }

    public int getRenderWindowWidth() {
        return renderWindowWidth;
    }

    public int getRenderWindowHeight() {
        return renderWindowHeight;
    }

    public int getRenderPlayerScreenX() {
        return renderPlayerScreenX;
    }

    public int getRenderPlayerScreenY() {
        return renderPlayerScreenY;
    }

    public float getRenderMapX() {
        return renderMapX;
    }

    public float getRenderMapY() {
        return renderMapY;
    }

    public String getRedirectHost() {
        return redirectHost;
    }

    public int getRedirectPort() {
        return redirectPort;
    }

    public String getRedirectName() {
        return redirectName;
    }

    public String getRedirectUriTemplate() {
        return redirectUriTemplate;
    }

    public boolean isRedirectSendChat() {
        return redirectSendChat;
    }

    public boolean isRedirectSendInfo() {
        return redirectSendInfo;
    }

    public boolean isRedirectKick() {
        return redirectKick;
    }

    public long getRedirectKickDuration() {
        return redirectKickDuration;
    }

    public String getRedirectChatTemplate() {
        return redirectChatTemplate;
    }

    public String getRedirectInfoTemplate() {
        return redirectInfoTemplate;
    }

    public String getRedirectKickTemplate() {
        return redirectKickTemplate;
    }
}
