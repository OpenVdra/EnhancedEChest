package com.enhancedechest.update;

import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    public static final String MODRINTH_PAGE = "https://modrinth.com/plugin/xgEWccga";
    private static final String MODRINTH_API  = "https://api.modrinth.com/v2/project/xgEWccga/version";
    private static final Pattern MODRINTH_VERSION_PATTERN =
            Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    public static final String GITHUB_RELEASES =
            "https://github.com/OpenVdra/EnhancedEChest/releases/latest";
    private static final String GITHUB_API =
            "https://api.github.com/repos/OpenVdra/EnhancedEChest/releases/latest";
    private static final Pattern GITHUB_TAG_PATTERN =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GITHUB_HTML_URL_PATTERN =
            Pattern.compile("\"html_url\"\\s*:\\s*\"(https://github\\.com/[^\"]+/releases/[^\"]+)\"");

    @Getter private final String currentVersion;
    private final Logger log;

    @Getter private volatile String latestVersion;
    /** Page users are pointed at to download the update. Defaults to Modrinth, switches to the
     *  GitHub release page when the Modrinth lookup fails and the GitHub fallback succeeds. */
    @Getter private volatile String downloadUrl = MODRINTH_PAGE;
    @Getter private volatile boolean updateAvailable = false;

    public UpdateChecker(String currentVersion, Logger log) {
        this.currentVersion = currentVersion;
        this.log = log;
    }

    public void checkAsync(FoliaLib foliaLib) {
        foliaLib.getScheduler().runAsync(task -> this.performCheck());
    }

    private void performCheck() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String version = fetchModrinthVersion(client);
        String source = MODRINTH_PAGE;

        if (version == null) {
            // Modrinth unreachable or returned nothing usable; fall back to GitHub releases.
            String[] github = fetchGithubLatest(client);
            if (github != null) {
                version = github[0];
                source = github[1];
            }
        }

        if (version == null) {
            log.warn("[UpdateChecker] Could not determine the latest version from Modrinth or GitHub.");
            return;
        }

        latestVersion = version;
        downloadUrl = source;
        if (isNewer(version, currentVersion)) {
            updateAvailable = true;
            printUpdateBanner();
        }
    }

    /** @return the latest Modrinth version_number, or {@code null} if it could not be read. */
    private String fetchModrinthVersion(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "EnhancedEchest/" + currentVersion + " (update-check)")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            Matcher matcher = MODRINTH_VERSION_PATTERN.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception e) {
            log.warn("[UpdateChecker] Could not reach Modrinth: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @return a two-element array {@code [version, releaseUrl]} from the latest GitHub release,
     *         or {@code null} if it could not be read. The leading {@code v} of a tag is stripped.
     */
    private String[] fetchGithubLatest(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "EnhancedEchest/" + currentVersion + " (update-check)")
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            Matcher tag = GITHUB_TAG_PATTERN.matcher(response.body());
            if (!tag.find()) return null;
            String version = tag.group(1).replaceFirst("^[vV]", "");

            Matcher html = GITHUB_HTML_URL_PATTERN.matcher(response.body());
            String url = html.find() ? html.group(1) : GITHUB_RELEASES;
            return new String[]{version, url};
        } catch (Exception e) {
            log.warn("[UpdateChecker] Could not reach GitHub: {}", e.getMessage());
            return null;
        }
    }

    private void printUpdateBanner() {
        String sep = "——————————————[ EnhancedEchest ]——————————————";
        log.warn("> {}", sep);
        log.warn(">");
        log.warn(">   UPDATE AVAILABLE!");
        log.warn(">   Current  : {}", currentVersion);
        log.warn(">   Latest   : {}", latestVersion);
        log.warn(">   Download : {}", downloadUrl);
        log.warn(">");
        log.warn("> {}", sep);
    }

    // Returns true if `latest` is strictly newer than `current`.
    private static boolean isNewer(String latest, String current) {
        int[] l = parseVersion(latest);
        int[] c = parseVersion(current);
        int len = Math.max(l.length, c.length);
        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? l[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        // Strip pre-release suffixes (-SNAPSHOT, -RC1, etc.) before splitting.
        version = version.replaceAll("[^0-9.].*", "");
        String[] parts = version.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return nums;
    }
}
