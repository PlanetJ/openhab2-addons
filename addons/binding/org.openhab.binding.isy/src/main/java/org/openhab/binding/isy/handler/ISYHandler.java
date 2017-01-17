/**
 * Copyright (c) 2014-2016 by txhe respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.isy.handler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.discovery.ISYDeviceDiscovery;
import org.openhab.binding.isy.internal.InsteonAddress;
import org.openhab.binding.isy.internal.NodeAddress;
import org.openhab.binding.isy.internal.WebsocketEventClient;
import org.openhab.binding.isy.internal.WebsocketEventHandler;
import org.openhab.binding.isy.internal.protocol.Event;
import org.openhab.binding.isy.internal.protocol.Node;
import org.openhab.binding.isy.internal.protocol.Nodes;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.openhab.binding.isy.internal.protocol.SubscriptionResponse;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * The {@link ISYHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jason Marchioni - Initial contribution
 */
public class ISYHandler extends BaseBridgeHandler implements WebsocketEventHandler {

    private Logger logger = LoggerFactory.getLogger(ISYHandler.class);

    private String baseUrl;
    private HttpClient httpClient;
    private WebsocketEventClient websocketEventClient;
    private ServiceRegistration<DiscoveryService> discoveryServiceRegistration;
    private XStream xStream;

    private Map<InsteonAddress, Thing> childThings = Collections.synchronizedMap(new HashMap<InsteonAddress, Thing>());

    public ISYHandler(Bridge thing) {
        super(thing);
        // Configure Xstream to deserialize the responses
        xStream = new XStream(new StaxDriver());
        xStream.ignoreUnknownElements();
        xStream.setClassLoader(ISYDeviceDiscovery.class.getClassLoader());
        xStream.processAnnotations(new Class[] { Nodes.class, Node.class, Properties.class, Property.class, Event.class,
                SubscriptionResponse.class });

    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (!childThing.getProperties().containsKey("address")) {
            throw new IllegalArgumentException("childThing does not have required 'address' property");
        }

        String address = childThing.getProperties().get("address");
        InsteonAddress insteonAddress = InsteonAddress.parseNodeAddress(address);
        childThings.put(insteonAddress, childThing);
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childThing.getProperties().containsKey("address")) {
            childThings.remove(InsteonAddress.parseNodeAddress(childThing.getProperties().get("address")));
        }
    }

    @Override
    public void initialize() {
        Configuration conf = getConfig();
        baseUrl = (String) conf.get(ISYBindingConstants.PARAMETER_BASE_URL);
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        httpClient = new HttpClient();
        String username = (String) conf.get(ISYBindingConstants.PARAMETER_USERNAME);
        String password = (String) conf.get(ISYBindingConstants.PARAMETER_PASSWORD);
        AuthenticationStore store = httpClient.getAuthenticationStore();
        try {
            store.addAuthentication(new BasicAuthentication(new URI(baseUrl), "/", username, password));
            httpClient.start();
        } catch (URISyntaxException e1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid baseUrl");
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }

        ContentResponse response;
        try {
            response = createRestRequest("config").send();
            if (response.getStatus() == HttpStatus.OK_200) {
                updateStatus(ThingStatus.ONLINE);
                startSubscribe(username, password);
            } else if (response.getStatus() == HttpStatus.FORBIDDEN_403
                    || response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Invalid response status " + response.getStatus());
            }
        } catch (TimeoutException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Timeout");
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            logger.error("Error communicatiing with ISY", e);
        }
    }

    public void startSubscribe(String username, String password) {
        String websocketUri = baseUrl.replace("http", "ws") + "/rest/subscribe";
        websocketEventClient = new WebsocketEventClient(websocketUri, username, password, xStream, scheduler);
        websocketEventClient.setHandler(this);
        websocketEventClient.start();
    }

    public Request createRestRequest(String request) {
        if (httpClient == null || !httpClient.isStarted()) {
            throw new IllegalStateException("httpClient is not started");
        }

        return httpClient.newRequest(baseUrl + "/rest" + (request.startsWith("/") ? "" : "/") + request)
                .accept("text/xml", "application/xml").header("Connection", "Keep-Alive");
    }

    public Nodes getNodes() {
        try {
            String response = createRestRequest("/nodes").send().getContentAsString();
            return (Nodes) xStream.fromXML(response);
        } catch (Exception e) {
            logger.error("Error retrieving nodes ", e);
            return null;
        }
    }

    public void sendNodeCommandOn(NodeAddress address) {
        sendNodeCommandOn(address, 100, false);
    }

    public void sendNodeCommandOn(NodeAddress address, int percentage) {
        sendNodeCommandOn(address, percentage, false);
    }

    public void sendNodeCommand(NodeAddress address, boolean fast) {
        sendNodeCommandOn(address, 100, fast);
    }

    public void sendNodeCommandOn(NodeAddress address, int percentage, boolean fast) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Value must be between 0 and 100");
        }

        int byteValue = Math.round(255f * (percentage / 100f));
        try {
            String command = fast ? "DFON" : "DON/" + byteValue;
            createRestRequest("/nodes/" + encodeAddress(address) + "/cmd/" + command).send();
        } catch (Exception e) {
            logger.error("Error sending 'on' command to address {} with percentage {}", address, percentage);
        }
    }

    public void sendNodeCommandOff(NodeAddress address) {
        sendNodeCommandOff(address, false);
    }

    public void sendNodeCommandOff(NodeAddress address, boolean fast) {
        try {
            String command = fast ? "DFOF" : "DOF";
            createRestRequest("/nodes/" + encodeAddress(address) + "/cmd/" + command).send();
        } catch (Exception e) {
            logger.error("Error sending 'off' command to address {}", address);
        }
    }

    public void sendNodeCommandDim(NodeAddress address) {
        try {
            createRestRequest("/nodes/" + encodeAddress(address) + "/cmd/DIM").send();
        } catch (Exception e) {
            logger.error("Error sending 'dim' command to address {}", address);
        }
    }

    public void sendNodeCommandBright(NodeAddress address) {
        try {
            createRestRequest("/nodes/" + encodeAddress(address) + "/cmd/BRT").send();
        } catch (Exception e) {
            logger.error("Error sending 'brt' command to address {}", address);
        }
    }

    public Properties getStatus(NodeAddress address) {
        try {
            String response = createRestRequest("/status/" + encodeAddress(address)).send().getContentAsString();
            return (Properties) xStream.fromXML(response);
        } catch (Exception e) {
            logger.error("Error ", e);
            return null;
        }
    }

    public String encodeAddress(String address) {
        return address.replace(" ", "%20");
    }

    public String encodeAddress(NodeAddress nodeAddress) {
        return nodeAddress.getAddress().replace(" ", "%20");
    }

    @Override
    public void nodePropertyUpdated(String address, String property, String value) {
        Thing child = childThings.get(InsteonAddress.parseNodeAddress(address));
        if (child != null && child.getHandler() instanceof WebsocketEventHandler) {
            ((WebsocketEventHandler) child.getHandler()).nodePropertyUpdated(address, property, value);
        }
    }

    @Override
    public void dispose() {
        if (httpClient != null) {
            try {
                httpClient.stop();
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            if (discoveryServiceRegistration != null) {
                logger.debug("Unregistering discovery service");
                discoveryServiceRegistration.unregister();
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            if (websocketEventClient != null && websocketEventClient.isStarted()) {
                websocketEventClient.stop();
                websocketEventClient.setHandler(null);
            }
        } catch (Exception e) {
            // ignore
        }
        discoveryServiceRegistration = null;
        httpClient = null;
        websocketEventClient = null;
    }
}
