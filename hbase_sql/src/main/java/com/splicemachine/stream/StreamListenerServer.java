/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.stream;


import com.google.common.net.HostAndPort;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.pipeline.Exceptions;
import com.splicemachine.stream.handlers.OpenHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.log4j.Logger;
import org.spark_project.guava.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;


/**
 * Created by dgomezferro on 5/20/16.
 */
@ChannelHandler.Sharable
public class StreamListenerServer<T> extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = Logger.getLogger(StreamListenerServer.class);
    private final int port;

    private Channel serverChannel;
    private Map<UUID, StreamListener> listenersMap = new ConcurrentHashMap<>();

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private HostAndPort hostAndPort;
    private CloseHandler closeHandler = new CloseHandler();

    public StreamListenerServer(int port) {
        this.port = port;
    }

    public void register(StreamListener listener) {
        listenersMap.put(listener.getUuid(), listener);
    }

    public void unregister(StreamListener listener) {
        listenersMap.remove(listener.getUuid());
    }

    public void start() throws StandardException {
        ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("StreamerListenerServer-boss-%s").build();
        this.bossGroup = new NioEventLoopGroup(4, tf);
        tf = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("StreamerListenerServer-worker-%s").build();
        this.workerGroup = new NioEventLoopGroup(4, tf);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new OpenHandler(this))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(port).sync();

            this.serverChannel = f.channel();
            InetSocketAddress socketAddress = (InetSocketAddress)this.serverChannel.localAddress();
            String host = InetAddress.getLocalHost().getHostName();
            int port = socketAddress.getPort();

            this.hostAndPort = HostAndPort.fromParts(host, port);
            LOG.info("StreamListenerServer listening on " + hostAndPort);

        } catch (IOException e) {
            throw Exceptions.parseException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
        LOG.error("Exception caught", cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel channel = ctx.channel();

        if (msg instanceof StreamProtocol.Init) {
            StreamProtocol.Init init = (StreamProtocol.Init) msg;

            LOG.trace("Received " + msg + " from " + channel);
            UUID uuid = init.uuid;
            int numPartitions = init.numPartitions;
            int partition = init.partition;

            final StreamListener<T> listener = listenersMap.get(uuid);
            if (listener != null) {
                // ... and hand off the channel to the listener
                listener.accept(ctx, numPartitions, partition);
                listener.addCloseable(new AutoCloseable() {
                    @Override
                    public void close() throws Exception {
                        unregister(listener);
                    }
                });
            } else {
                // Listener deregistered, request close of channel
                LOG.warn("Listener not found, must have unregistered");
                ctx.writeAndFlush(new StreamProtocol.RequestClose());
                ctx.pipeline().addLast(closeHandler);
            }
            // Remove this listener ...
            ctx.pipeline().remove(this);
        } else {
            // ERROR
            LOG.error("Unexpected message, expecting Init, received: " + msg);
        }
    }

    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }
}

@ChannelHandler.Sharable
class CloseHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof StreamProtocol.ConfirmClose) {
            ctx.close();
        } else {
            // ignore all other messages
        }
    }
}
