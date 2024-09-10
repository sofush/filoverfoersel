package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

public class TcpServer implements Runnable, Closeable {
    private final ServerSocketChannel socket;
    private final Selector selector;

    public TcpServer(int port) throws IOException {
        this.socket = ServerSocketChannel.open();
        this.socket.bind(new InetSocketAddress(port));
        this.socket.configureBlocking(false);

        this.selector = Selector.open();
        this.socket.register(this.selector, SelectionKey.OP_ACCEPT);
    }

    public void acceptClient() {
        try {
            SocketChannel client = this.socket.accept();
            if (client == null) return;

            System.out.println("Server has accepted a client.");
            client.configureBlocking(false);
            client.register(this.selector, SelectionKey.OP_READ);
            System.out.println("The new client has been registered for read events.");
        } catch (Exception e) {
            System.err.println("Could not connect and register client: " + e);
        }
    }

    public URL readRequest(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer inBuffer = ByteBuffer.allocate(1024);
        int read;

        try {
            read = client.read(inBuffer);
            System.out.println("Read " + read + " bytes from client.");
        } catch (Exception e) {
            System.err.println("Could not read from client, closing connection.");
            key.cancel();
            return null;
        }

        if (read == 0) return null;

        byte[] requestedFileBytes = Arrays.copyOf(inBuffer.array(), read);
        String requestedFile = new String(requestedFileBytes, StandardCharsets.UTF_8);
        System.out.println("Client requested a file: " + requestedFile);

        URL url = App.class.getClassLoader().getResource(requestedFile);

        if (url == null) {
            System.err.println("Client requested a file that does not exist.");
        }

        return url;
    }

    public void sendFileToClient(SelectionKey key, URL fileToSend) {
        SocketChannel client = (SocketChannel) key.channel();
        FileInputStream input;

        try {
            input = new FileInputStream(fileToSend.getFile());
        } catch (FileNotFoundException e) {
            System.err.println("Could not find file: " + fileToSend);
            key.cancel();
            return;
        }

        try (input) {
            System.out.println("Sending the contents of the to the client.");
            ByteBuffer outBuffer = ByteBuffer.allocate(1024);
            int read = 0;

            while (read != -1) {
                read = input.read(
                        outBuffer.array(),
                        outBuffer.position(),
                        outBuffer.remaining()
                );

                if (read >= 0)
                    outBuffer.position(read);

                if (!outBuffer.hasRemaining()) {
                    outBuffer.flip();
                    client.write(outBuffer);
                    outBuffer.clear();
                    System.out.println("Wrote " + outBuffer.capacity() + " bytes of the file to the client.");
                }
            }

            if (outBuffer.position() != 0) {
                int count = outBuffer.position();
                outBuffer.flip();
                client.write(outBuffer);
                System.out.println("Wrote " + count + " bytes of the file to the client.");
            }

            client.close();
        } catch (Exception e) {
            System.err.println("Could not send file to client: " + e);
            key.cancel();
        }
    }

    @Override
    public void run() {
        while (this.socket.isOpen() && this.selector.isOpen() && !Thread.interrupted()) {
            try {
                System.out.println("Server is waiting for I/O readiness...");
                this.selector.select();
            } catch (Exception e) {
                System.err.println("Could not select: " + e);
                continue;
            }

            Set<SelectionKey> keys = this.selector.selectedKeys();

            for (var key : keys) {
                Channel ch = key.channel();

                if (ch instanceof ServerSocketChannel && key.isAcceptable()) {
                    System.out.println("Attempting to accept an incoming client connection...");
                    this.acceptClient();
                } else if (ch instanceof SocketChannel) {
                    if (key.isReadable()) {
                        System.out.println("Reading client request...");
                        URL fileToSend = this.readRequest(key);
                        if (fileToSend == null || !key.isValid()) continue;

                        key.interestOps(SelectionKey.OP_WRITE);
                        key.attach(fileToSend);
                    } else if (key.isWritable()) {
                        System.out.println("Sending client response...");
                        URL fileToSend = (URL) key.attachment();
                        this.sendFileToClient(key, fileToSend);
                        key.cancel();
                    }
                }
            }

            keys.clear();
        }
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
        this.selector.close();
    }
}
