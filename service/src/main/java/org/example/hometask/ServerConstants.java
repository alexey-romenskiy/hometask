package org.example.hometask;

import static java.util.Objects.requireNonNull;

public final class ServerConstants {

    public static final int RPC_STREAM = Integer.getInteger("org.example.hometask.RPC_STREAM");
    public static final String SERVER_URI = requireNonNull(System.getProperty("org.example.hometask.SERVER_URI"));

    private ServerConstants() {
        //nothing
    }
}
