package org.example.hometask;

import static java.util.Objects.requireNonNull;

public final class ClientConstants {

    public static final int RPC_STREAM = Integer.getInteger("org.example.hometask.RPC_STREAM");
    public static final String SERVER_URI = requireNonNull(System.getProperty("org.example.hometask.SERVER_URI"));
    public static final String CLIENT_URI = requireNonNull(System.getProperty("org.example.hometask.CLIENT_URI"));

    private ClientConstants() {
        //nothing
    }
}
