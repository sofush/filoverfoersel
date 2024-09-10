package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;

public class App {
    public static void main(String[] args) {
        final String filename = "test.txt";
        final String host = "localhost";
        final int port = 3000;

        switch (args[0].toLowerCase()) {
            case "client" -> {
                var address = new InetSocketAddress(host, port);
                try (TcpClient client = new TcpClient(address, filename)) {
                    client.run();
                } catch (IOException e) {
                    System.err.println("Could not connect to server.");
                }
            }
            case "server" -> {
                try (TcpServer server = new TcpServer(port)) {
                    server.run();
                } catch (IOException e) {
                    System.err.println("Could not start server on port " + port + ".");
                }
            }
            default -> System.out.println("Program requires an argument: `client` or `server`.");
        }
    }
}
