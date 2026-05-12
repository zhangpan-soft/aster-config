package io.asterconfig.server.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "aster")
public class AsterConfigProperties {

    private Profile profile = Profile.LOCAL;
    private Path dataDir = Path.of("./data/aster-config");
    private Embed embed = new Embed();

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Embed getEmbed() {
        return embed;
    }

    public void setEmbed(Embed embed) {
        this.embed = embed == null ? new Embed() : embed;
    }

    public boolean isEmbedPermitAll() {
        if (embed.getPermitAll() != null) {
            return embed.getPermitAll();
        }
        return profile == Profile.LOCAL;
    }

    public enum Profile {
        LOCAL,
        INTEGRATED
    }

    public static class Embed {

        private Boolean permitAll;

        public Boolean getPermitAll() {
            return permitAll;
        }

        public void setPermitAll(Boolean permitAll) {
            this.permitAll = permitAll;
        }
    }
}
