package com.danielflower.apprunner.router;

/**
 * An entry point for running the server locally that uses sample-config.properties for config.
 */
public class RunLocal {

    public static void main(String[] args) {
        App.main(new String[] { "sample-config.properties" });
    }

}
