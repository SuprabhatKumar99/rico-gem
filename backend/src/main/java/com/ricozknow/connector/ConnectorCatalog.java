package com.ricozknow.connector;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** Provider-neutral catalog. Providers can be added by registering an adapter + OAuth credentials. */
public final class ConnectorCatalog {
    private ConnectorCatalog() {}
    public record Spec(String id, String name, String authEndpoint, String tokenEndpoint,
                       String scope, boolean pkce, boolean webhookSupported) {}
    public static final Map<String, Spec> SPECS = Map.of(
      "google-drive", new Spec("google-drive","Google Drive", "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token", "https://www.googleapis.com/auth/drive.readonly", true,false),
      "notion", new Spec("notion","Notion", "https://api.notion.com/v1/oauth/authorize", "https://api.notion.com/v1/oauth/token", "", false,false),
      "confluence", new Spec("confluence","Confluence", "https://auth.atlassian.com/authorize", "https://auth.atlassian.com/oauth/token", "read:page:confluence read:space:confluence offline_access", true,false),
      "sharepoint", new Spec("sharepoint","SharePoint", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize", "https://login.microsoftonline.com/common/oauth2/v2.0/token", "offline_access Files.Read.All Sites.Read.All",true,false),
      "slack", new Spec("slack","Slack", "https://slack.com/oauth/v2/authorize", "https://slack.com/api/oauth.v2.access", "channels:read,channels:history,groups:read,groups:history",false,true),
      "github", new Spec("github","GitHub", "https://github.com/login/oauth/authorize", "https://github.com/login/oauth/access_token", "repo read:org",false,true),
      "jira", new Spec("jira","Jira", "https://auth.atlassian.com/authorize", "https://auth.atlassian.com/oauth/token", "read:jira-work offline_access",true,false)
    );
    public static Spec require(String provider) {
        Spec spec = SPECS.get(provider);
        if (spec == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown connector");
        return spec;
    }
    public static List<Spec> ordered() { return List.of(SPECS.get("google-drive"),SPECS.get("notion"),SPECS.get("confluence"),SPECS.get("sharepoint"),SPECS.get("slack"),SPECS.get("github"),SPECS.get("jira")); }
}
