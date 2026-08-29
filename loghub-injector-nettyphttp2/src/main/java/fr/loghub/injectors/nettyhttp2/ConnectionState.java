package fr.loghub.injectors.nettyhttp2;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future.State;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.concurrent.Future;

public class ConnectionState {
    private static final Logger log = LogManager.getLogger();

    final EventLoopGroup group;
    private final String host;
    private final int port;
    private final String path;
    private final boolean useTls;

    private final ChannelFuture connected;
    private final AtomicReference<CompletableFuture<Long>> pingFuture = new AtomicReference<>();
    private long lastPublish;
    final ThreadLocal<StreamState> threadLocalStream;
    final Set<StreamState> activeStreams = Collections.newSetFromMap(new ConcurrentHashMap<>());
    final AtomicBoolean active = new AtomicBoolean(false);

    ConnectionState(EventLoopGroup group, boolean useTls, String host, int port, String path) {
        this.group = group;
        this.host = host;
        this.port = port;
        this.path = path;
        this.useTls = useTls;
        this.threadLocalStream = ThreadLocal.withInitial(() -> new StreamState(this));
        active.set(true);
        connected = connect();
    }

    private ChannelFuture connect() {
        Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                                      .option(ChannelOption.SO_KEEPALIVE, true)
                                      .handler(new ChannelInitializer<SocketChannel>() {
                                          @Override
                                          protected void initChannel(SocketChannel ch) throws Exception {
                                              if (useTls) {
                                                  SslContext sslCtx = SslContextBuilder.forClient()
                                                                              .sslProvider(SslProvider.JDK)
                                                                              .applicationProtocolConfig(
                                                                                      new ApplicationProtocolConfig(
                                                                                              ApplicationProtocolConfig.Protocol.ALPN,
                                                                                              ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                                                                              ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                                                                              ApplicationProtocolNames.HTTP_2))
                                                                              .build();
                                                  ch.pipeline().addLast(
                                                          sslCtx.newHandler(ch.alloc(), host, port));
                                              }
                                              ch.pipeline().addLast(
                                                      Http2FrameCodecBuilder.forClient()
                                                                            .initialSettings(
                                                                                Http2Settings.defaultSettings()
                                                                            ).frameLogger(
                                                                                new Http2FrameLogger(LogLevel.DEBUG)
                                                                            ).build(),
                                                      new Http2MultiplexHandler(
                                                              new SimpleChannelInboundHandler<Http2StreamFrame>() {
                                                                  @Override
                                                                  protected void channelRead0(ChannelHandlerContext ctx,
                                                                          Http2StreamFrame msg) {
                                                                      log.debug(msg);
                                                                  }
                                                              }),
                                                      getControlFrameHandler()
                                              );
                                          }
                                      });
        bootstrap.remoteAddress(host, port);
        ChannelFuture future = bootstrap.connect()
                                        .addListener(f -> this.lastPublish = System.nanoTime());
        log.debug("New channel {}", future.channel());
        return future;
    }

    private ChannelHandler getControlFrameHandler() {
        return new SimpleChannelInboundHandler<Http2Frame>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Http2Frame msg) {
                if (msg instanceof Http2SettingsAckFrame || msg instanceof Http2SettingsFrame) {
                    log.debug("Consumed control frame: {}", msg);
                } else if (msg instanceof Http2PingFrame pf && pf.ack()) {
                    pingAck(pf);
                } else {
                    log.debug("Forwarding control frame: {}", msg);
                    ctx.fireChannelRead(msg);
                }
            }
        };

    }

    public Http2Headers getHeaders() {
        return new DefaultHttp2Headers().method(HttpMethod.POST.asciiName()).path(path)
                       .scheme(useTls ? "https" : "http").authority(host + ":" + port)
                       .add("content-type", "application/cbor");
    }

    synchronized boolean ping() {
        if (System.nanoTime() > this.lastPublish + 1_000_000_000) {
            CompletableFuture<Long> waitingPing = pingFuture.updateAndGet(this::waitPing);
            if (!waitingPing.isDone()) {
                try {
                    waitingPing.get();
                    this.lastPublish = System.nanoTime();
                } catch (InterruptedException e) {
                    close();
                    Thread.currentThread().interrupt();
                    return false;
                } catch (ExecutionException e) {
                    close();
                    return false;
                }
            }
            return waitingPing.state() == State.SUCCESS;
        }
        lastPublish = System.nanoTime();
        return true;
    }

    private CompletableFuture<Long> waitPing(CompletableFuture<Long> localPingPromise) {
        // Called from holder reference, if null, no ping was active, forward a new one
        if (localPingPromise == null) {
            long pingTime = System.nanoTime();
            CompletableFuture<Long> expecting = new CompletableFuture<>();
            connected.channel().writeAndFlush(new DefaultHttp2PingFrame(pingTime, false)).addListener(f -> {
                if (!f.isSuccess()) {
                    log.atError().withThrowable(f.cause()).log("Connection {} failed to ping: {}",
                            connected::channel, () -> f.cause().getMessage());
                    expecting.completeExceptionally(f.cause());
                    close().sync();
                }
            });
            return expecting;
        } else {
            return localPingPromise;
        }
    }

    private void pingAck(Http2PingFrame frame) {
        pingFuture.updateAndGet(f -> {
            if (f == null || f.state() != State.RUNNING) {
                log.info("Unexpected ping ack");
            } else {
                f.complete(frame.content());
            }
            return null;
        });
        lastPublish = System.nanoTime();
    }

    public boolean isActive() {
        return active.get();
    }

    public ChannelFuture close() {
        log.debug("closing {}", connected.channel());
        if (active.compareAndSet(true, false)) {
            // Flusher tous les streams actifs avec un frame endStream=true
            // Itérer sur une copie car finish() modifie activeStreams
            for (StreamState state : Set.copyOf(activeStreams)) {
                log.debug("Flushing stream {} on close", state.stream.stream().id());
                state.finish(true);
            }
        }
        return connected.channel().close();
    }

    public void update() {
        this.lastPublish = System.nanoTime();
    }

    Future<Http2StreamChannel> makeChannel(StreamState cs) {
        return new Http2StreamChannelBootstrap(connected.channel()).handler(cs.getHandler()).open();
    }

    StreamState getAlive() {
        try {
            connected.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
        StreamState state = threadLocalStream.get();
        if (! state.isActive() || ! state.ping()) {
            state.finish(false);
            state = threadLocalStream.get();
        }
        state.ping();
        return state;
    }

}
