package fr.loghub.injectors.nettyhttp2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future.State;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrame;

class StreamState {

    private static final Logger log = LogManager.getLogger();

    private final ConnectionState connection;
    final Http2StreamChannel stream;
    private final Thread creatorThread;
    private final AtomicReference<CompletableFuture<Http2Headers>> rsetFuture = new AtomicReference<>();
    // A latch that will be released when end of stream is reached
    private final CountDownLatch endStream = new CountDownLatch(1);

    StreamState(ConnectionState connection) {
        this.connection = connection;
        try {
            this.stream = makeChannel();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        connection.activeStreams.add(this);
        creatorThread = Thread.currentThread();
    }

    SimpleChannelInboundHandler<Http2StreamFrame> getHandler() {
        return new SimpleChannelInboundHandler<>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                switch (msg) {
                case Http2ResetFrame rf -> {
                    finish(false);
                    long errorCode = rf.errorCode();
                    int streamId = rf.stream().id();
                    log.debug("Stream closed: stream={}, errorCode={} ({})", () -> streamId, () -> errorCode,
                            () -> Http2Error.valueOf(errorCode));
                    endStream.countDown();
                }
                case Http2HeadersFrame hf -> responseHeaders(hf);
                case Http2DataFrame df when df.isEndStream() -> endStream.countDown();
                default -> log.debug("Response {}", msg);
                }
            }

            @Override
            public boolean acceptInboundMessage(Object msg) {
                return msg instanceof Http2StreamFrame;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                finish(false);
                ctx.close();
            }
        };
    }

    private void responseHeaders(Http2HeadersFrame frame) {
        rsetFuture.updateAndGet(f -> {
            if (f == null || f.state() != State.RUNNING) {
                log.info("Unexpected headers {}", frame::headers);
            } else {
                f.complete(frame.headers());
            }
            return f;
        });
        if (frame.isEndStream()) {
            endStream.countDown();
        }
    }

    private Http2StreamChannel makeChannel() throws ExecutionException, InterruptedException {
        return connection.makeChannel(this).addListener(f -> {
            if (f.isSuccess()) {
                Http2StreamChannel channel = (Http2StreamChannel) f.get();
                sendHeader(channel);
            }
        }).get();
    }

    private void sendHeader(Http2StreamChannel channel) {
        Http2Headers headers = connection.getHeaders();
        CompletableFuture<Void> headersFuture = new CompletableFuture<>();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(headers, false)).addListener(f -> {
            if (f.isSuccess()) {
                headersFuture.complete(null);
            } else {
                log.atWarn()
                   .withThrowable(f.cause())
                   .log("Failed to send HEADERS frame: {}", () -> f.cause().getMessage());
                headersFuture.completeExceptionally(f.cause());
            }
        });
    }

    boolean isActive() {
        return stream.isActive();
    }

    boolean ping() {
        return connection.ping();
    }

    /**
     * Clôture proprement le stream : envoie un DATA frame vide {@code endStream=true}
     * si {@code sendEndStream} est true, retire ce state du {@link ConnectionState#activeStreams}
     * et supprime l'entrée du {@link ConnectionState#threadLocalStream}.
     *
     * @param sendEndStream true pour envoyer le frame de fin de stream
     */
    void finish(boolean sendEndStream) {
        try {
            if (sendEndStream && isActive()) {
                stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, true)).sync();
            }
            Http2Headers headers = rsetFuture.updateAndGet(f -> f == null ? new CompletableFuture<>() : f).get();
            CharSequence status = headers.status();
            HttpResponseStatus httpStatus = HttpResponseStatus.parseLine(status);
            if (httpStatus.code() > 200) {
                log.error("Publication failed");
            }
            endStream.await();
            stream.close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.atError().withThrowable(e.getCause()).log("Failed to close HTTP/2 stream {}", stream);
        }
        if (creatorThread == Thread.currentThread()) {
            connection.threadLocalStream.remove();
        }
        connection.activeStreams.remove(this);
    }

}
