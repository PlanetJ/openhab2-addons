package org.openhab.binding.isy.internal;

import java.awt.geom.Area;
import java.net.URI;
import java.util.concurrent.Executors;
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
import org.openhab.binding.isy.discovery.ISYDeviceDiscovery;
import org.openhab.binding.isy.internal.protocol.Event;
import org.openhab.binding.isy.internal.protocol.EventInfo;
import org.openhab.binding.isy.internal.protocol.Group;
import org.openhab.binding.isy.internal.protocol.Node;
import org.openhab.binding.isy.internal.protocol.Nodes;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.openhab.binding.isy.internal.protocol.RestResponse;
import org.openhab.binding.isy.internal.protocol.StateVariable;
import org.openhab.binding.isy.internal.protocol.SubscriptionResponse;
import org.openhab.binding.isy.internal.protocol.VariableList;
import org.openhab.binding.isy.internal.protocol.elk.AreaEvent;
import org.openhab.binding.isy.internal.protocol.elk.Areas;
import org.openhab.binding.isy.internal.protocol.elk.ElkStatus;
import org.openhab.binding.isy.internal.protocol.elk.Topology;
import org.openhab.binding.isy.internal.protocol.elk.Zone;
import org.openhab.binding.isy.internal.protocol.elk.ZoneEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

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
        // client.setConnectTimeout(5000);
        // client.setExecutor(executorService);
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
            Object messageObj;
            try {
                messageObj = xStream.fromXML(message);
            } catch (Exception e) {
                logger.error("Error parsing message [{}]", message);
                return;
            }
            if (messageObj instanceof Event) {
                Event event = (Event) messageObj;
                logger.debug("Node '{}' got control message '{}' action '{}'",
                        Strings.isNullOrEmpty(event.getNode()) ? "n/a" : event.getNode(), event.getControl(),
                        event.getAction());
                if (!event.getControl().startsWith("_")) {
                    if (handler != null) {
                        handler.nodePropertyUpdated(event.getNode(), event.getControl(), event.getAction());
                    }
                } else if ("_19".equals(event.getControl())) {
                    // Elk events
                    if ("3".equals(event.getAction())) {
                        // Zone event
                        ZoneEvent zoneEvent = event.getEventInfo().getZoneEvent();
                        logger.debug(zoneEvent.toString());
                        if (handler != null) {
                            handler.elkZoneEvent(zoneEvent);
                        }
                    } else if ("2".equals(event.getAction())) {
                        // Area Event
                        AreaEvent areaEvent = event.getEventInfo().getAreaEvent();
                        logger.debug(areaEvent.toString());
                        if (handler != null) {
                            handler.elkAreaEvent(areaEvent);
                        }
                    }
                }
            }
        }
    }

    public static void main(String args[]) {
        // Configure Xstream to deserialize the responses
        XStream xStream = new XStream(new StaxDriver());
        xStream.ignoreUnknownElements();
        xStream.setClassLoader(ISYDeviceDiscovery.class.getClassLoader());
        xStream.processAnnotations(new Class[] { Nodes.class, Node.class, Properties.class, Property.class, Event.class,
                EventInfo.class, SubscriptionResponse.class, Group.class, VariableList.class, StateVariable.class,
                StateVariable.ValueType.class, Topology.class, Areas.class, Area.class, Zone.class, ZoneEvent.class,
                AreaEvent.class, ElkStatus.class, RestResponse.class });

        WebsocketEventClient client = new WebsocketEventClient(args[0], args[1], args[2], xStream,
                Executors.newScheduledThreadPool(2));
        client.start();
    }

}
