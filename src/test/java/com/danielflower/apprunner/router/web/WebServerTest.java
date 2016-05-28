package com.danielflower.apprunner.router.web;

import com.danielflower.apprunner.router.AppEstate;
import com.danielflower.apprunner.router.FileSandbox;
import com.danielflower.apprunner.router.web.v1.AppResource;
import com.danielflower.apprunner.router.web.v1.SystemResource;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class WebServerTest {

    private HttpClient client;
    private WebServer webServer;
    private ProxyMap proxyMap = new ProxyMap();
    private TestServer appServer;

    @Before
    public void setup() throws Exception {
        client = new HttpClient();
        client.setFollowRedirects(false);
        client.start();
        AppEstate estate = new AppEstate(proxyMap, fileSandbox());
        webServer = new WebServer(0, proxyMap, "test-app",
            new SystemResource(new AtomicBoolean(true)), new AppResource(estate));
        webServer.start();
        appServer = new TestServer();
    }

    public static FileSandbox fileSandbox() {
        File root = new File("target/test-sandboxes/" + UUID.randomUUID());
        try {
            FileUtils.forceMkdir(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new FileSandbox(root);
    }

    @After
    public void stop() throws Exception {
        client.stop();
        webServer.close();
        appServer.close();
    }

    @Test
    public void aRequestToTheRootResultsInARedirect() throws Exception {
        proxyMap.add("test-app", appServer.url);

        ContentResponse resp = client.GET(webServer.baseUrl() + "/");
        assertThat(resp.getStatus(), is(302));
        String location = resp.getHeaders().get("Location");
        assertThat(location, is(webServer.baseUrl() + "/test-app"));
        resp = client.GET(location);
        location = resp.getHeaders().get("Location");
        assertThat(resp.getStatus(), is(302));
        assertThat(location, is(webServer.baseUrl() + "/test-app/"));

    }

    @Test
    public void prefixesCanBeProxiedToOtherServices() throws Exception {
        proxyMap.add("sample-app", appServer.url);

        ContentResponse resp = client.GET(webServer.baseUrl() + "/sample-app/");
        assertThat(resp.getContentAsString(), containsString("Hello from test server"));
        assertThat(resp.getStatus(), is(200));
    }

    @Test
    public void nonProxiedAndOtherNonSpecialURLsResultIn404s() throws Exception {
        ContentResponse resp = client.GET(webServer.baseUrl() + "/blahblabhlbah");
        assertThat(resp.getStatus(), is(404));
    }


    private static class TestServer implements AutoCloseable {
        private final Server jettyServer;
        final URL url;

        public TestServer() throws Exception {
            jettyServer = new Server(0);
            jettyServer.setHandler(new AbstractHandler() {
                public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                    if (target.equals("/test-app")) {
                        response.sendRedirect("/test-app/");
                    } else {
                        response.setHeader("Server", "Test-Server");
                        response.getWriter().append("Hello from test server").close();
                    }
                    baseRequest.setHandled(true);
                }
            });

            jettyServer.start();

            int port = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
            url = new URL("http://localhost:" + port + "/test-app");
        }

        public void close() throws Exception {
            jettyServer.stop();
            jettyServer.join();
        }
    }
}
