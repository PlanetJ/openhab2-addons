package org.openhab.binding.isy.internal;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.isy.internal.protocol.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;

/**
 *
 *
 * @author jmarchioni
 *
 */

@WebSocket
public class WebsocketEventClient {

    private Logger logger = LoggerFactory.getLogger(WebsocketEventClient.class);

    private final String url;
    private final String username;
    private final String password;
    private final XStream xStream;
    private final ScheduledExecutorService executorService;

    private WebSocketClient client;
    private Session session;
    private WebsocketEventHandler handler;
    private WebsocketHandler socketHandler = new WebsocketHandler();

    public WebsocketEventClient(String url, String username, String password, XStream xStream,
            ScheduledExecutorService executorService) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.xStream = xStream;
        this.executorService = executorService;
    }

    public void setHandler(WebsocketEventHandler handler) {
        this.handler = handler;
    }

    public boolean start() {
        if (session != null && session.isOpen()) {
            throw new IllegalStateException("Already started");
        }

        ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
        upgradeRequest.setSubProtocols("ISYSUB");
        upgradeRequest.setHeader("Sec-WebSocket-Version", "13");
        upgradeRequest.setHeader("Authorization",
                "Basic " + Base64.encodeBase64URLSafeString(new String(username + ":" + password).getBytes()));
        upgradeRequest.setHeader("Origin", "com.universal-devices.websockets.isy");

        client = new WebSocketClient();
        try {
            client.start();
            client.connect(socketHandler, new URI(url), upgradeRequest);
            return true;
        } catch (Exception e) {
            logger.error("Error starting isy websocket subscription", e);
            return false;
        }

    }

    public void stop() {
        if (client == null) {
            throw new IllegalStateException("Can't stop when never started");
        }
        try {
            if (session != null) {
                session.close(StatusCode.NORMAL, "Stopping");
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            try {
                client.stop();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public boolean isStarted() {
        return client != null && !client.isStopped();
    }

    @WebSocket
    public class WebsocketHandler {

        @OnWebSocketClose
        public void onClose(int arg1, String arg2) {
            logger.debug("Closing");
            executorService.schedule(new Runnable() {

                @Override
                public void run() {
                    start();
                }
            }, 10, TimeUnit.SECONDS);
        }

        @OnWebSocketConnect
        public void onConnect(Session newSession) {
            logger.debug("Connected");
            session = newSession;
        }

        @OnWebSocketMessage
        public void onMessage(String message) {
            logger.trace("Got message [{}]", message);
            Object messageObj = xStream.fromXML(message);
            if (messageObj instanceof Event) {
                Event event = (Event) messageObj;
                logger.debug("Node {} got control message '{}' [{}]]", event.getNode(), event.getControl(),
                        event.getAction());
                if (!event.getControl().startsWith("_")) {
                    if (handler != null) {
                        handler.nodePropertyUpdated(event.getNode(), event.getControl(), event.getAction());
                    }
                }
            }
        }
    }

}
