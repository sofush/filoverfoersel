package org.example;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TcpClient implements Runnable, Closeable {
    private final SocketChannel socket;
    private final Selector selector;
    private final String filename;

    public TcpClient(SocketAddress serverAddress, String filename) throws IOException {
        this.filename = filename;
        this.selector = Selector.open();
        this.socket = SocketChannel.open();
        this.socket.connect(serverAddress);
        this.socket.configureBlocking(false);
        this.socket.register(this.selector, SelectionKey.OP_READ);
        System.out.println("Client has connected to the server.");
    }

    @Override
    public void run() {
        byte[] contents = this.filename.getBytes(StandardCharsets.UTF_8);
        ByteBuffer outBuffer = ByteBuffer.wrap(contents);

        try {
            System.out.println("Requesting a file from the server...");
            this.socket.write(outBuffer);
        } catch (Exception e) {
            System.err.println("Could not write request to server: " + e);
        }

        ByteBuffer inBuffer = ByteBuffer.allocate(1024);

        try {
            System.out.println("Reading response from the server...");
            int read = 0;

            while (read != -1 && this.socket.isConnected()) {
                this.selector.select(50);
                read = this.socket.read(inBuffer);

                if (read > 0)
                    System.out.println("Read " + read + " bytes from the server.");

                if (!inBuffer.hasRemaining()) {
                    ByteBuffer newBuffer = ByteBuffer.allocate(inBuffer.capacity() * 2);
                    inBuffer.flip();
                    newBuffer.put(inBuffer);
                    inBuffer = newBuffer;
                    System.out.println("Grew input buffer to " + inBuffer.capacity() + " bytes.");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read response from server: " + e);
            return;
        }

        byte[] contentBytes = Arrays.copyOfRange(inBuffer.array(), 0, inBuffer.position());
        String content = new String(contentBytes, StandardCharsets.UTF_8);
        System.out.println("File contents: " + content);
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}
