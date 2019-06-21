package io.quarkus.network;

import io.quarkus.runtime.annotations.Template;

@Template
public class NetworkTemplate {
    public static boolean networkSetup;

    public void setup() {
        System.out.println("********** NETWORK SETUP **********");
        networkSetup = true;
    }
}
