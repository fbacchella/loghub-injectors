package loghub.injectors.nettyhttp2;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;

public class Http2Client implements AutoCloseable {

    private static final Logger log = LogManager.getLogger();

    final String host;
    final int port;
    final String path;
    final boolean useTls;

    private final EventLoopGroup group;
    private final AtomicReference<ConnectionState> connection = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Http2Client(URI webhookUri) {
        this.host = webhookUri.getHost();
        this.port = webhookUri.getPort();
        String rawPath = webhookUri.getPath();
        this.path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;
        this.useTls = "https".equals(webhookUri.getScheme());
        this.group = new NioEventLoopGroup(1);
    }

    private synchronized ConnectionState getConnectionState() {
        ConnectionState currentConnection = connection.get();
        if (closed.get()) {
            if (currentConnection != null) {
                currentConnection.close();
            }
            connection.set(null);
            return null;
        } else if (currentConnection != null && !currentConnection.isActive()) {
            connection.set(null);
        }
        if (currentConnection == null) {
            currentConnection = new ConnectionState(group, useTls, host, port, path);
            connection.set(currentConnection);
        }
        return currentConnection;
    }

    /**
     * Publie le contenu d'un {@link ByteBuffer} sur le stream HTTP/2 du thread courant.
     * Le stream est conservé dans un {@link ThreadLocal} et réutilisé entre les appels.
     * Si le stream est invalide ou fermé, un nouveau stream est ouvert automatiquement.
     *
     * @param data données à envoyer
     * @return CompletableFuture complété quand le DATA frame final est envoyé
     */
    public Future<Void> publish(byte[] data) {
        ConnectionState cs = getConnectionState();
        if (cs == null) {
            return ImmediateEventExecutor.INSTANCE.newFailedFuture(new IllegalStateException("Sink closed"));
        }
        StreamState state = cs.getAlive();
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        Http2StreamChannel stream = state.stream;
        Http2DataFrame df = new DefaultHttp2DataFrame(buf, false);
        return stream.writeAndFlush(df)
                .addListener(writeFuture -> {
                    if (! writeFuture.isSuccess()) {
                        log.atError()
                           .withThrowable(writeFuture.cause())
                           .log("Failed to send message for stream {}", () -> stream.stream().id());
                        state.finish(false);
                    } else {
                        cs.update();
                    }
                });
    }

    @Override
    public void close() {
        log.debug("Closing");
        if (closed.compareAndSet(false, true)) {
            Optional.ofNullable(connection.getAndSet(null))
                    .map(ConnectionState::close)
                    .ifPresent(cf -> cf.addListener(f -> {
                        log.info("Connection closed: {}", f);
                        group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
                    }));
        }
    }

}
