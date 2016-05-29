package com.danielflower.apprunner.router.web;

import com.danielflower.apprunner.router.mgmt.Cluster;
import com.danielflower.apprunner.router.mgmt.Runner;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ReverseProxy extends AsyncProxyServlet {
    public static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    public static final Pattern APP_REQUEST = Pattern.compile("/([^/?]+)(.*)");
    public static final Pattern APP_API = Pattern.compile("/api/v1/apps/([^/?]+)(.*)");

    private final ProxyMap proxyMap;
    private final Cluster cluster;

    public ReverseProxy(Cluster cluster, ProxyMap proxyMap) {
        this.cluster = cluster;
        this.proxyMap = proxyMap;
    }

    protected String filterServerResponseHeader(HttpServletRequest clientRequest, Response serverResponse, String headerName, String headerValue) {
        if (headerName.equalsIgnoreCase("location")) {
            URI targetUri = serverResponse.getRequest().getURI();
            String toReplace = targetUri.getScheme() + "://" + targetUri.getAuthority();
            if (headerValue.startsWith(toReplace)) {
                headerValue = clientRequest.getScheme() + "://" + clientRequest.getHeader("host")
                    + headerValue.substring(toReplace.length());
                log.info("Rewrote location header to " + headerValue);
                return headerValue;
            }
        }
        return super.filterServerResponseHeader(clientRequest, serverResponse, headerName, headerValue);
    }

    protected String rewriteTarget(HttpServletRequest clientRequest) {
        String uri = clientRequest.getRequestURI();
        String query = isEmpty(clientRequest.getQueryString()) ? "" : "?" + clientRequest.getQueryString();

        log.info(clientRequest.getMethod() + " " + uri);
        Matcher matcher = APP_REQUEST.matcher(uri);
        if (uri.startsWith("/api/")) {
            if (uri.equals("/api/v1/apps")) {
                String method = clientRequest.getMethod().toUpperCase();
                if (method.equals("POST")) {
                    Optional<Runner> targetRunner = cluster.allocateRunner(proxyMap.getAll());
                    if (targetRunner.isPresent()) {
                        URI targetAppRunner = targetRunner.get().url;
                        return targetAppRunner.resolve(uri) + query;
                    } else {
                        log.error("There are no app runner instances available! Add another instance or change the maxApps value of an existing one.");
                        return null;
                    }
                }
            } else {
                Matcher appApiMatcher = APP_API.matcher(uri);
                if (appApiMatcher.matches()) {
                    String prefix = appApiMatcher.group(1);
                    // TODO: proxy to app runner
                    return cluster.getRunners().get(0).url.resolve(uri) + query;
                }
            }
        } else if (matcher.matches()) {
            String prefix = matcher.group(1);
            URL url = proxyMap.get(prefix);
            if (url != null) {
                String newTarget = url.toString() + matcher.group(2) + query;
                log.info("Proxying to " + newTarget);
                return newTarget;
            }
        }

        log.info("No proxy target configured for " + uri);
        return null;
    }

    @Override
    protected void onServerResponseHeaders(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Response serverResponse) {
        super.onServerResponseHeaders(clientRequest, proxyResponse, serverResponse);
        if (isAppCreationPost(clientRequest)) {
            String appName = proxyResponse.getHeader("Location");
            appName = appName.substring(appName.lastIndexOf("/") + 1);
            if (proxyResponse.getStatus() == 201) {
                try {
                    URI targetAppRunnerURI = serverResponse.getRequest().getURI().resolve("/" + appName);
                    proxyMap.add(appName, targetAppRunnerURI.toURL());
                } catch (MalformedURLException e) {
                    log.error("Could not write proxy value", e);
                }
            } else {
                // decrement instance count for the runner
            }
        }
    }

    private static boolean isAppCreationPost(HttpServletRequest clientRequest) {
        return clientRequest.getMethod().equals("POST") && clientRequest.getRequestURI().equals("/api/v1/apps");
    }

    protected void onProxyRewriteFailed(HttpServletRequest clientRequest, HttpServletResponse proxyResponse) {
        // this is called if rewriteTarget returns null
        int status;
        String message;
        if (isAppCreationPost(clientRequest)) {
            status = 503;
            message = "There are no App Runner instances with free capacity";
        } else {
            status = 404;
            message = "404 Not Found";
        }
        try {
            proxyResponse.getWriter().write(message);
        } catch (IOException e) {
            log.info("Could not write error", e);
        }
        sendProxyResponseError(clientRequest, proxyResponse, status);
    }

    protected void addProxyHeaders(HttpServletRequest clientRequest, Request proxyRequest) {
        super.addProxyHeaders(clientRequest, proxyRequest);
        proxyRequest.getHeaders().remove("Host");
        proxyRequest.header("Host", clientRequest.getHeader("Host"));
    }
}
