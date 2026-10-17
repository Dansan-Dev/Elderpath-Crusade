package io.github.elderpath_crusade.net.transport;

import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;

import java.util.function.Consumer;

/**
 * ClientChannel/ServerConnection over any LineLink, encoding every message through
 * MessageCodec — so an in-process match exercises exactly the serialization a TCP one does.
 */
public final class LineChannels {
    private LineChannels() {}

    public static ClientChannel clientChannel(LineLink link) {
        return new ClientChannel() {
            @Override
            public void send(ServerMessage message) {
                link.send(MessageCodec.encode(message));
            }

            @Override
            public ClientMessage poll() {
                String line = link.pollLine();
                return line == null ? null : MessageCodec.decodeClient(line);
            }

            @Override
            public boolean isOpen() {
                return link.isOpen();
            }

            @Override
            public void close() {
                link.close();
            }
        };
    }

    /** rawTap (may be null) sees every received line before it is decoded — for leak tests and logs. */
    public static ServerConnection serverConnection(LineLink link, Consumer<String> rawTap) {
        return new ServerConnection() {
            @Override
            public void send(ClientMessage message) {
                link.send(MessageCodec.encode(message));
            }

            @Override
            public ServerMessage poll() {
                String line = link.pollLine();
                if (line == null) return null;
                if (rawTap != null) rawTap.accept(line);
                return MessageCodec.decodeServer(line);
            }

            @Override
            public boolean isOpen() {
                return link.isOpen();
            }

            @Override
            public void close() {
                link.close();
            }
        };
    }
}
