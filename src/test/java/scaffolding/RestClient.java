package scaffolding;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;

import java.net.URI;
import java.net.URLEncoder;

public class RestClient implements AutoCloseable {

    public static RestClient create(String appRunnerUrl) {
        HttpClient c = new HttpClient();
        try {
            c.start();
            if (appRunnerUrl.endsWith("/")) {
                appRunnerUrl = appRunnerUrl.substring(0, appRunnerUrl.length() - 1);
            }
            return new RestClient(c, appRunnerUrl);
        } catch (Exception e) {
            throw new RuntimeException("Unable to make client", e);
        }
    }

    private final HttpClient client;
    public final String routerUrl;

    private RestClient(HttpClient client, String routerUrl) {
        this.client = client;
        this.routerUrl = routerUrl;
    }

    public ContentResponse createApp(String gitUrl, String appName) throws Exception {
        Fields fields = new Fields();
        fields.add("gitUrl", gitUrl);
        if (appName != null) {
            fields.add("appName", appName);
        }
        return client.POST(routerUrl + "/api/v1/apps")
            .content(new FormContentProvider(fields)).send();
    }

    public ContentResponse deploy(String app) throws Exception {
        return client.POST(routerUrl + "/api/v1/apps/" + app + "/deploy")
            .header("Accept", "application/json") // to simulate products like the Stash commit hook
            .send();
    }

    public ContentResponse stop(String app) throws Exception {
        return client.newRequest(routerUrl + "/api/v1/apps/" + app + "/stop").method("PUT").send();
    }

    public ContentResponse deleteApp(String appName) throws Exception {
        return client.newRequest(routerUrl + "/api/v1/apps/" + appName).method("DELETE").send();
    }

    public ContentResponse homepage(String appName) throws Exception {
        return client.GET(routerUrl + "/" + appName + "/");
    }

    public ContentResponse get(String url) throws Exception {
        return client.GET(routerUrl + url);
    }

    public ContentResponse registerRunner(String id, URI url, int maxInstances) throws Exception {
        Fields fields = new Fields();
        fields.add("id", id);
        fields.add("url", url.toString());
        fields.add("maxApps", String.valueOf(maxInstances));
        return client.POST(routerUrl + "/api/v1/runners")
            .content(new FormContentProvider(fields)).send();
    }


    public ContentResponse getAppRunners() throws Exception {
        return get("/api/v1/runners");
    }
    public ContentResponse getRunner(String id) throws Exception {
        return get("/api/v1/runners/" + URLEncoder.encode(id, "UTF-8"));
    }

    public ContentResponse deleteRunner(String id) throws Exception {
        return client.newRequest(routerUrl + "/api/v1/runners/" + URLEncoder.encode(id, "UTF-8")).method("DELETE").send();
    }

    @Override
    public void close() throws Exception {
        client.stop();
    }
}
