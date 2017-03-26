/**
 * Copyright (c) 2014-2016 by txhe respective copyright holders.

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.isy.handler;

import static org.openhab.binding.isy.ISYBindingConstants.*;

import java.awt.geom.Area;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.isy.ISYBindingConstants;
import org.openhab.binding.isy.discovery.ISYDeviceDiscovery;
import org.openhab.binding.isy.internal.CacheHolder;
import org.openhab.binding.isy.internal.ElkAddress;
import org.openhab.binding.isy.internal.ElkAddress.Type;
import org.openhab.binding.isy.internal.InsteonAddress;
import org.openhab.binding.isy.internal.InsteonAddress.InsteonAddressChannel;
import org.openhab.binding.isy.internal.NodeAddress;
import org.openhab.binding.isy.internal.RequestFailedException;
import org.openhab.binding.isy.internal.SceneAddress;
import org.openhab.binding.isy.internal.WebsocketEventClient;
import org.openhab.binding.isy.internal.WebsocketEventHandler;
import org.openhab.binding.isy.internal.ZwaveAddress;
import org.openhab.binding.isy.internal.protocol.Event;
import org.openhab.binding.isy.internal.protocol.EventInfo;
import org.openhab.binding.isy.internal.protocol.Group;
import org.openhab.binding.isy.internal.protocol.Node;
import org.openhab.binding.isy.internal.protocol.Nodes;
import org.openhab.binding.isy.internal.protocol.Properties;
import org.openhab.binding.isy.internal.protocol.Property;
import org.openhab.binding.isy.internal.protocol.RestResponse;
import org.openhab.binding.isy.internal.protocol.StateVariable;
import org.openhab.binding.isy.internal.protocol.StateVariable.ValueType;
import org.openhab.binding.isy.internal.protocol.SubscriptionResponse;
import org.openhab.binding.isy.internal.protocol.VariableList;
import org.openhab.binding.isy.internal.protocol.VariableStatus;
import org.openhab.binding.isy.internal.protocol.elk.AreaEvent;
import org.openhab.binding.isy.internal.protocol.elk.Areas;
import org.openhab.binding.isy.internal.protocol.elk.ElkStatus;
import org.openhab.binding.isy.internal.protocol.elk.Topology;
import org.openhab.binding.isy.internal.protocol.elk.Zone;
import org.openhab.binding.isy.internal.protocol.elk.ZoneEvent;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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

    public static final String PROPERY_STATUS = "ST";

    private String baseUrl;
    private HttpClient httpClient;
    private WebsocketEventClient websocketEventClient;
    private ServiceRegistration<DiscoveryService> discoveryServiceRegistration;
    private XStream xStream;

    private Map<InsteonAddress, Thing> insteonThings = Collections
            .synchronizedMap(new HashMap<InsteonAddress, Thing>());
    private Map<ElkAddress, Thing> elkThings = Collections.synchronizedMap(new HashMap<ElkAddress, Thing>());
    private Map<ZwaveAddress, Thing> zwaveThings = Collections.synchronizedMap(new HashMap<ZwaveAddress, Thing>());

    private CacheHolder<Nodes> nodes;
    private CacheHolder<ElkStatus> elkStatus;
    private CacheHolder<Nodes> nodeStatus;
    private CacheHolder<VariableList> stateVariables;
    private CacheHolder<Topology> elkTopology;
    private CacheHolder<VariableStatus> stateVariableStatus;

    private ScheduledFuture<?> scheduledRefresh;
    private ListeningScheduledExecutorService listeningExecutor;

    public ISYHandler(Bridge thing) {
        super(thing);
        // Configure Xstream to deserialize the responses
        xStream = new XStream(new StaxDriver());
        xStream.ignoreUnknownElements();
        xStream.setClassLoader(ISYDeviceDiscovery.class.getClassLoader());
        xStream.processAnnotations(new Class[] { Nodes.class, Node.class, Properties.class, Property.class, Event.class,
                EventInfo.class, SubscriptionResponse.class, Group.class, VariableList.class, StateVariable.class,
                StateVariable.ValueType.class, Topology.class, Areas.class, Area.class, Zone.class, ZoneEvent.class,
                AreaEvent.class, ElkStatus.class, RestResponse.class, VariableStatus.class });

        nodes = new CacheHolder<>(30000, new CacheHolder.Loader<Nodes>() {

            @Override
            public ListenableFuture<Nodes> call() throws Exception {
                logger.debug("Loading nodes");
                return Futures.transform(restRequest("/nodes", Nodes.class), new Function<Nodes, Nodes>() {

                    @Override
                    public Nodes apply(Nodes nodes) {
                        logger.debug("Indexing nodes");
                        nodes.index();
                        return nodes;
                    }

                });
            }
        });

        elkStatus = new CacheHolder<>(30000, new CacheHolder.Loader<ElkStatus>() {

            @Override
            public ListenableFuture<ElkStatus> call() throws Exception {
                logger.debug("Loading elk status");
                return restRequest("/elk/get/status", ElkStatus.class);
            }
        });

        nodeStatus = new CacheHolder<>(30000, new CacheHolder.Loader<Nodes>() {

            @Override
            public ListenableFuture<Nodes> call() throws Exception {
                logger.debug("Loading node status");
                return Futures.transform(restRequest("/status", Nodes.class), new Function<Nodes, Nodes>() {

                    @Override
                    public Nodes apply(Nodes nodes) {
                        logger.debug("Indexing nodes");
                        nodes.index();
                        return nodes;
                    }
                });
            }

        });

        stateVariables = new CacheHolder<>(30000, new CacheHolder.Loader<VariableList>() {

            @Override
            public ListenableFuture<VariableList> call() throws Exception {
                logger.debug("Loading state variable defintions");
                return restRequest("/vars/definitions/2", VariableList.class);
            }

        });

        elkTopology = new CacheHolder<>(30000, new CacheHolder.Loader<Topology>() {

            @Override
            public ListenableFuture<Topology> call() throws Exception {
                logger.debug("Loading elk topology");
                return restRequest("/elk/get/topology", Topology.class);
            }
        });

        stateVariableStatus = new CacheHolder<>(30000, new CacheHolder.Loader<VariableStatus>() {

            @Override
            public ListenableFuture<VariableStatus> call() throws Exception {
                logger.debug("Loading state variable status");
                return Futures.transform(restRequest("/vars/get/2", VariableStatus.class),
                        new Function<VariableStatus, VariableStatus>() {

                            @Override
                            public VariableStatus apply(VariableStatus vars) {
                                logger.debug("Indexing state variable status");
                                vars.index();
                                return vars;
                            }

                        });
            }
        });

    }

    @Override
    public void initialize() {
        listeningExecutor = MoreExecutors.listeningDecorator(scheduler);

        Configuration conf = getConfig();
        baseUrl = (String) conf.get(ISYBindingConstants.PARAMETER_BASE_URL);
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        httpClient = new HttpClient();
        // httpClient.setExecutor(Executors.newFixedThreadPool(2));
        httpClient.setMaxConnectionsPerDestination(2);

        final String username = (String) conf.get(ISYBindingConstants.PARAMETER_USERNAME);
        final String password = (String) conf.get(ISYBindingConstants.PARAMETER_PASSWORD);
        AuthenticationStore store = httpClient.getAuthenticationStore();
        try {
            store.addAuthentication(new BasicAuthentication(new URI(baseUrl), "/", username, password));
            httpClient.start();
        } catch (URISyntaxException e1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid baseUrl");
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }

        Futures.addCallback(restRequest("config"), new FutureCallback<ContentResponse>() {

            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof TimeoutException) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Timeout");
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, throwable.getMessage());
                    logger.error("Error communicatiing with ISY", throwable);
                }
            }

            @Override
            public void onSuccess(ContentResponse response) {
                if (response.getStatus() == HttpStatus.OK_200) {
                    updateStatus(ThingStatus.ONLINE);
                    startSubscribe(username, password);
                    loadChannels();
                } else if (response.getStatus() == HttpStatus.FORBIDDEN_403
                        || response.getStatus() == HttpStatus.UNAUTHORIZED_401) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Invalid response status " + response.getStatus());
                }
            }
        });
        scheduleRefresh();
    }

    @Override
    public void dispose() {
        if (scheduledRefresh != null) {
            scheduledRefresh.cancel(true);
            scheduledRefresh = null;
        }
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

    private void scheduleRefresh() {
        int interval = Integer.parseInt(getConfig().get(ISYBindingConstants.PARAMETER_REFRESH).toString());
        logger.info("Scheduling refresh updates at {} seconds", interval);
        scheduledRefresh = scheduler.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                logger.debug("Running scheduled refresh");
                Futures.addCallback(nodeStatus.get(), new FutureCallback<Nodes>() {

                    @Override
                    public void onFailure(Throwable arg0) {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void onSuccess(Nodes nodes) {
                        for (Node node : nodes.getNodes()) {
                            String address = node.getAddress() != null ? node.getAddress() : node.getId();
                            for (Property prop : node.getProperies()) {
                                nodePropertyUpdated(address, prop.getId(), prop.getValue());
                            }
                        }
                    }
                });

                Futures.addCallback(elkStatus.get(), new FutureCallback<ElkStatus>() {

                    @Override
                    public void onFailure(Throwable arg0) {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void onSuccess(ElkStatus status) {
                        for (AreaEvent event : status.getAreas()) {
                            elkAreaEvent(event);
                        }
                        for (ZoneEvent event : status.getZones()) {
                            elkZoneEvent(event);
                        }
                    }

                });
                stateVariableStatus.get();
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private void loadChannels() {
        final ThingBuilder thingBuilder = editThing();
        Futures.addCallback(Futures.allAsList(nodes.get(), stateVariables.get()), new FutureCallback<List<?>>() {

            @Override
            public void onFailure(Throwable arg0) {
                logger.info("Loading scenes or variables failed, scheduling a retry");
                scheduler.schedule(new Runnable() {

                    @Override
                    public void run() {
                        loadChannels();
                    }
                }, 15, TimeUnit.SECONDS);
            }

            @Override
            public void onSuccess(List<?> results) {
                Nodes nodes = (Nodes) results.get(0);
                VariableList varList = (VariableList) results.get(1);
                if (loadScenes(nodes, thingBuilder) && loadStateVariables(varList, thingBuilder)) {
                    updateThing(thingBuilder.build());
                } else {
                    logger.info("Loading scenes or variables failed, scheduling a retry");
                    scheduler.schedule(new Runnable() {

                        @Override
                        public void run() {
                            loadChannels();
                        }
                    }, 15, TimeUnit.SECONDS);
                }
            }
        });
    }

    private boolean loadScenes(Nodes nodes, ThingBuilder thingBuilder) {
        if (nodes != null && nodes.getGroups() != null) {
            removeChannels(thingBuilder, ISYBindingConstants.CHANNEL_SCENE);
            for (Group group : nodes.getGroups()) {
                if (group.getName().equals("ISY")) {
                    continue;
                }
                thingBuilder.withChannel(ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), "scene" + group.getAddress()), "Switch")
                        .withProperties(Collections.singletonMap("address", group.getAddress()))
                        .withType(ISYBindingConstants.CHANNEL_TYPE_SCENE).withLabel(group.getName()).build());
            }
            return true;
        }
        return false;
    }

    private boolean loadStateVariables(VariableList variables, ThingBuilder thingBuilder) {
        if (variables != null && variables.getStateVariables() != null) {
            removeChannels(thingBuilder, ISYBindingConstants.CHANNEL_STATE_VARIABLE);
            for (StateVariable stateVariable : variables.getStateVariables()) {
                Configuration defaultConfig = new Configuration();
                defaultConfig.put("bidirectional", false);
                thingBuilder.withChannel(ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), "stateVariable" + stateVariable.getId()), "Number")
                        .withConfiguration(defaultConfig).withType(ISYBindingConstants.CHANNEL_TYPE_STATE_VARIABLE)
                        .withLabel(stateVariable.getName())
                        .withProperties(Collections.singletonMap("id", stateVariable.getId())).build());
            }
            return true;
        }
        return false;
    }

    public ListenableFuture<Nodes> getNodes() {
        return nodes.get();
    }

    public ListenableFuture<ElkStatus> getElkStatus() {
        return elkStatus.get();
    }

    public ListenableFuture<Topology> getElkTopology() {
        return elkTopology.get();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Channel {} got command {}", channelUID.getAsString(), command.toString());
        Channel channel = getThing().getChannel(channelUID.getId());
        if (channel != null) {
            if (CHANNEL_TYPE_SCENE.equals(channel.getChannelTypeUID())) {
                handleSceneCommand(channel, command);
            } else if (CHANNEL_TYPE_STATE_VARIABLE.equals(channel.getChannelTypeUID())) {
                handleVariableCommand(channel, command);
            }
        }
    }

    private void handleVariableCommand(final Channel channel, Command anyCommand) {
        String id = channel.getProperties().get("id");
        Boolean isBidirectional = (Boolean) channel.getConfiguration().get("bidirectional");
        if (isBidirectional != null && isBidirectional && anyCommand instanceof DecimalType) {
            int value = ((DecimalType) anyCommand).intValue();
            setStateVariable(id, value);
        } else if (anyCommand == RefreshType.REFRESH) {
            Futures.addCallback(getStateVariable(id), new FutureCallback<Integer>() {

                @Override
                public void onFailure(Throwable arg0) {

                }

                @Override
                public void onSuccess(Integer value) {
                    if (value != null) {
                        updateState(channel.getUID(), new DecimalType(value));
                    }
                }
            });
        }
    }

    private void handleSceneCommand(Channel channel, Command anyCommand) {
        String sceneAddress = channel.getProperties().get("address");
        logger.debug("Scene {} got {} command", channel.getLabel(), anyCommand);
        OnOffType command = null;
        if (anyCommand instanceof OnOffType) {
            command = (OnOffType) anyCommand;
        } else if (anyCommand instanceof StringType) {
            try {
                command = OnOffType.valueOf(anyCommand.toFullString());
            } catch (Exception e) {
                // bad command
                return;
            }
        } else if (anyCommand instanceof DecimalType) {
            int value = ((DecimalType) anyCommand).intValue();
            command = value > 0 ? OnOffType.ON : OnOffType.OFF;
        } else {
            // not supported
            return;
        }

        if (command == OnOffType.ON) {
            sendNodeCommandOn(new SceneAddress(sceneAddress));
        } else {
            sendNodeCommandOff(new SceneAddress(sceneAddress));
        }

    }

    @Override
    public void handleUpdate(ChannelUID channelUID, State newState) {
        logger.debug("Channel {} got update {}", channelUID.getAsString(), newState.toString());
        Channel channel = getThing().getChannel(channelUID.getId());
        if (channel != null) {
            if (CHANNEL_TYPE_STATE_VARIABLE.equals(channel.getChannelTypeUID())) {
                String id = channel.getProperties().get("id");
                if (newState instanceof DecimalType) {
                    setStateVariable(id, ((DecimalType) newState).intValue());
                } else if (newState instanceof StringType) {
                    try {
                        OnOffType state = OnOffType.valueOf(newState.toFullString());
                        setStateVariable(id, state == OnOffType.ON ? 1 : 0);
                    } catch (Exception e) {
                        logger.error("Unknown string command {}", newState.toFullString());
                    }
                } else if (newState instanceof OnOffType) {
                    setStateVariable(id, newState == OnOffType.ON ? 1 : 0);
                }
            }
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {

        if (childHandler instanceof BaseInsteonHandler) {
            if (!childThing.getProperties().containsKey("address")) {
                throw new IllegalArgumentException("childThing does not have required 'address' property");
            }

            String address = childThing.getProperties().get("address");
            InsteonAddress insteonAddress = InsteonAddress.parseNodeAddress(address);
            insteonThings.put(insteonAddress, childThing);
        } else if (childHandler instanceof ElkZoneHandler) {
            ElkAddress address = ((ElkZoneHandler) childHandler).getAddress();
            elkThings.put(address, childThing);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof BaseInsteonHandler) {
            if (childThing.getProperties().containsKey("address")) {
                insteonThings.remove(InsteonAddress.parseNodeAddress(childThing.getProperties().get("address")));
            }
        } else if (childHandler instanceof ElkZoneHandler) {
            elkThings.remove(((ElkZoneHandler) childHandler).getAddress());
        }
    }

    private void removeChannels(ThingBuilder thingBuilder, String channelType) {
        for (Channel channel : getThing().getChannels()) {
            if (channel.getChannelTypeUID().getId().equals(channelType)) {
                thingBuilder.withoutChannel(channel.getUID());
            }
        }
    }

    public void startSubscribe(String username, String password) {
        String websocketUri = baseUrl.replace("http", "ws") + "/rest/subscribe";
        websocketEventClient = new WebsocketEventClient(websocketUri, username, password, xStream, scheduler);
        websocketEventClient.setHandler(this);
        websocketEventClient.start();
    }

    private ListenableFuture<ContentResponse> restRequest(final String requestUrl) {
        return restRequest(requestUrl, ContentResponse.class);
    }

    private <T> ListenableFuture<T> restRequest(final String requestUrl, final Class<T> clazz) {
        if (httpClient == null || !httpClient.isStarted()) {
            throw new IllegalStateException("httpClient is not started");
        }
        final Request request = httpClient
                .newRequest(baseUrl + "/rest" + (requestUrl.startsWith("/") ? "" : "/") + requestUrl)
                .accept("text/xml", "application/xml").header("Connection", "Keep-Alive");
        try {
            return listeningExecutor.submit(new Callable<T>() {

                @SuppressWarnings("unchecked")
                @Override
                public T call() throws Exception {
                    logger.debug("Making request to {}", request.getURI().toURL().toString());
                    ContentResponse contentResponse = request.send();
                    if (ContentResponse.class.isAssignableFrom(clazz)) {
                        return (T) contentResponse;
                    }

                    Object response = xStream.fromXML(contentResponse.getContentAsString());
                    if (response instanceof RestResponse && !RestResponse.class.isAssignableFrom(clazz)) {
                        throw new RequestFailedException((RestResponse) response);
                    } else {
                        return (T) response;
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error decoding request {}", request);
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
        String command = fast ? "DFON" : "DON/" + byteValue;
        restRequest("/nodes/" + encodeAddress(address) + "/cmd/" + command);
    }

    public void sendNodeCommandOff(NodeAddress address) {
        sendNodeCommandOff(address, false);
    }

    public void sendNodeCommandOff(NodeAddress address, boolean fast) {
        String command = fast ? "DFOF" : "DOF";
        restRequest("/nodes/" + encodeAddress(address) + "/cmd/" + command);
    }

    public void sendNodeCommandDim(NodeAddress address) {
        restRequest("/nodes/" + encodeAddress(address) + "/cmd/DIM");
    }

    public void sendNodeCommandBright(NodeAddress address) {
        restRequest("/nodes/" + encodeAddress(address) + "/cmd/BRT");
    }

    public ListenableFuture<Properties> getStatus(final NodeAddress address) {
        return Futures.transform(nodeStatus.get(), new Function<Nodes, Properties>() {

            @Override
            public Properties apply(Nodes nodes) {
                Node node = nodes.getNode(address);
                if (node != null) {
                    Properties properties = new Properties();
                    properties.setProperties(node.getProperies());
                    return properties;
                } else {
                    return null;
                }

            }
        });
    }

    public void setStateVariable(String id, int value) {
        logger.debug("Setting state for var {} to {}", id, value);
        restRequest("/vars/set/2/" + id + "/" + String.valueOf(value));
    }

    public ListenableFuture<Integer> getStateVariable(final String id) {
        return Futures.transform(stateVariableStatus.get(), new Function<VariableStatus, Integer>() {

            @Override
            public Integer apply(VariableStatus vars) {
                ValueType val = vars.getVariable(id);
                if (val != null) {
                    return Integer.parseInt(val.getValue());
                } else {
                    return null;
                }
            }
        });
    }

    public String encodeAddress(String address) {
        return address.replace(" ", "%20");
    }

    public String encodeAddress(NodeAddress nodeAddress) {
        return nodeAddress.getAddress().replace(" ", "%20");
    }

    @Override
    public void nodePropertyUpdated(String address, String property, String value) {
        InsteonAddress insteonAddress = null;
        try {
            insteonAddress = InsteonAddress.parseNodeAddress(address);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid insteon address {}", address);
            return;
        }

        Thing child = insteonThings.get(insteonAddress);
        if (child != null && child.getHandler() instanceof BaseInsteonHandler) {
            InsteonAddressChannel addressChannel = InsteonAddress.parseNodeAddressChannel(address);
            logger.debug("Sending property '{}' update to thing {}", property, child.getLabel());
            ((BaseInsteonHandler) child.getHandler()).propertyUpdated(addressChannel.getChannel(), property, value);
        }
    }

    @Override
    public void elkAreaEvent(AreaEvent areaEvent) {
        // TODO Auto-generated method stub

    }

    @Override
    public void elkZoneEvent(ZoneEvent zoneEvent) {
        ElkAddress address = new ElkAddress(Type.ZONE, zoneEvent.getZone());
        if (elkThings.containsKey(address)) {
            ((ElkZoneHandler) elkThings.get(address).getHandler()).handleZoneEvent(zoneEvent);
        }
    }

}
