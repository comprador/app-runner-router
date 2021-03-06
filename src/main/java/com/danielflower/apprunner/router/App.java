package com.danielflower.apprunner.router;

import com.danielflower.apprunner.router.mgmt.Cluster;
import com.danielflower.apprunner.router.mgmt.ClusterQueryingMapManager;
import com.danielflower.apprunner.router.mgmt.MapManager;
import com.danielflower.apprunner.router.web.ProxyMap;
import com.danielflower.apprunner.router.web.WebServer;
import com.danielflower.apprunner.router.web.v1.RunnerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;

public class App {
    public static final Logger log = LoggerFactory.getLogger(App.class);

    private final Config config;
    private WebServer webServer;

    public App(Config config) {
        this.config = config;
    }

    public void start() throws Exception {
        File dataDir = config.getOrCreateDir(Config.DATA_DIR);

        ProxyMap proxyMap = new ProxyMap();
        int appRunnerPort = config.getInt(Config.SERVER_PORT);


        String defaultAppName = config.get(Config.DEFAULT_APP_NAME, null);
        MapManager mapManager = ClusterQueryingMapManager.create(proxyMap);
        Cluster cluster = Cluster.load(new File(dataDir, "cluster.json"), mapManager);
        mapManager.loadAllApps(URI.create("/"), cluster.getRunners());

        String accessLogFilename = config.get("access.log.path", null);
        webServer = new WebServer(appRunnerPort, cluster, mapManager, proxyMap, defaultAppName, new RunnerResource(cluster), accessLogFilename);
        webServer.start();
    }

    public void shutdown() {
        log.info("Shutdown invoked");
        if (webServer != null) {
            log.info("Stopping web server");
            try {
                webServer.close();
            } catch (Exception e) {
                log.info("Error while stopping", e);
            }
            log.info("Shutdown complete");
            webServer = null;
        }
    }

    public static void main(String[] args) {
        try {
            App app = new App(Config.load(args));
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
