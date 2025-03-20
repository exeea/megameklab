package megameklab.util;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.awt.*;

import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import megamek.common.Entity;
import megamek.logging.MMLogger;
import megameklab.ui.MegaMekLabMainUI;
import megameklab.ui.MegaMekLabTabbedUI;

/**
 * Manages cross-process tab transfer between different instances of MegaMekLab.
 * Uses a combination of UDP broadcasts for discovery and TCP for data transfer.
 */
public class CrossProcessTabManager {
    private static final MMLogger logger = MMLogger.create(CrossProcessTabManager.class);

    // Network constants
    private static final int DISCOVERY_PORT = 47652;
    private static final String DISCOVERY_GROUP = "230.0.0.1";

    // Message types
    private static final String MSG_INSTANCE_ANNOUNCE = "ANNOUNCE";
    private static final String MSG_INSTANCE_SHUTDOWN = "SHUTDOWN";
    private static final String MSG_TAB_AVAILABLE = "TAB_AVAILABLE";
    private static final String MSG_TAB_ACCEPT = "TAB_ACCEPT";
    private static final String MSG_TAB_ACCEPTED = "TAB_ACCEPTED";

    private final MegaMekLabTabbedUI owner;
    private String instanceId;
    private MulticastSocket discoverySocket;
    private ServerSocket dataSocket;
    private Rectangle windowBounds = new Rectangle();
    private Thread discoveryListener;
    private Thread dataListener;
    private boolean running = false;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Integer> instancePorts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TabEntity> tabEntityRegistry = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> recentTabIds = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> tabAcceptanceFutures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MML-Tab-Transfer-Timeout");
        t.setDaemon(true);
        return t;
    });

    private class TabEntity {
        public Entity entity;
        public String filename;

        public TabEntity(Entity entity, String filename) {
            this.entity = entity;
            this.filename = filename;
        }
    }

    private static final int MAX_RECENT_TABS = 20;

    public CrossProcessTabManager(MegaMekLabTabbedUI owner) {
        this.owner = owner;
        this.instanceId = UUID.randomUUID().toString();
        this.objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running) {
                stop();
            }
        }));
    }

    /**
     * Starts the cross-process tab manager
     */
    public void start() {
        if (running) {
            return;
        }

        try {
            // Initialize multicast socket for discovery
            discoverySocket = new MulticastSocket(DISCOVERY_PORT);
            InetAddress group = InetAddress.getByName(DISCOVERY_GROUP);
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            discoverySocket.joinGroup(new InetSocketAddress(group, DISCOVERY_PORT), ni);

            // Initialize data socket for tab transfers
            dataSocket = new ServerSocket(0);
            logger.info("Using data port: " + dataSocket.getLocalPort());

            // Start discovery listener
            discoveryListener = new Thread(this::runDiscoveryListener, "MML-Discovery-Listener");
            discoveryListener.setDaemon(true);
            discoveryListener.start();

            // Start data listener
            dataListener = new Thread(this::runDataListener, "MML-Data-Listener");
            dataListener.setDaemon(true);
            dataListener.start();

            // Schedule periodic announcements
            Timer announceTimer = new Timer("MML-Announce-Timer", true);
            announceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    sendAnnouncement();
                }
            }, 1000, 5000);

            running = true;

            // Initial announcement
            sendAnnouncement();

            logger.info("Cross-process tab manager started, instance ID: " + instanceId);
        } catch (Exception e) {
            logger.error("Failed to start cross-process tab manager", e);
        }
    }

    /**
     * Stops the cross-process tab manager
     */
    public void stop() {
        if (!running) {
            return;
        }
        try {
            if (discoverySocket != null && !discoverySocket.isClosed()) {
                String message = String.format("%s|%s",
                        MSG_INSTANCE_SHUTDOWN,
                        instanceId);

                byte[] buffer = message.getBytes();
                InetAddress group = InetAddress.getByName(DISCOVERY_GROUP);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, DISCOVERY_PORT);
                discoverySocket.send(packet);
                logger.debug("Broadcasted shutdown message for instance: " + instanceId);
            }
        } catch (Exception e) {
            logger.error("Error broadcasting shutdown message", e);
        }
        running = false;
        scheduledExecutor.shutdownNow();
        for (CompletableFuture<Boolean> future : tabAcceptanceFutures.values()) {
            if (!future.isDone()) {
                future.complete(false);
            }
        }
        tabAcceptanceFutures.clear();
        if (discoveryListener != null) {
            discoveryListener.interrupt();
        }

        if (dataListener != null) {
            dataListener.interrupt();
        }

        try {
            if (discoverySocket != null) {
                discoverySocket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing discovery socket", e);
        }

        try {
            if (dataSocket != null) {
                dataSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing data socket", e);
        }

        logger.info("Cross-process tab manager stopped");
    }

    /**
     * Updates the window bounds of this application instance
     */
    public void updateWindowBounds(Rectangle bounds) {
        this.windowBounds = bounds;
    }

    /**
     * Sends periodic announcements to let other instances know about this one
     */
    private void sendAnnouncement() {
        try {
            // Get current window position
            SwingUtilities.invokeLater(() -> {
                windowBounds = owner.getBounds();
            });

            // Create announcement message
            String message = String.format("%s|%s|%d|%d|%d|%d|%d",
                    MSG_INSTANCE_ANNOUNCE,
                    instanceId,
                    dataSocket.getLocalPort(),
                    windowBounds.x,
                    windowBounds.y,
                    windowBounds.width,
                    windowBounds.height);

            // Send via multicast
            byte[] buffer = message.getBytes();
            InetAddress group = InetAddress.getByName(DISCOVERY_GROUP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, DISCOVERY_PORT);
            discoverySocket.send(packet);
        } catch (Exception e) {
            logger.error("Error sending announcement", e);
        }
    }

    /**
     * Offers a tab to other processes
     */
    private String offerTab(MegaMekLabMainUI ui, Rectangle ghostBounds) {
        try {
            Entity entity = ui.getEntity();
            String filename = ui.getFileName();
            String tabId = UUID.randomUUID().toString();

            tabEntityRegistry.put(tabId, new TabEntity(entity, filename));
            recentTabIds.add(tabId);
            while (recentTabIds.size() > MAX_RECENT_TABS) {
                String oldestId = recentTabIds.poll();
                if (oldestId != null) {
                    tabEntityRegistry.remove(oldestId);
                }
            }

            // Create message
            String message = String.format("%s|%s|%s|%d|%d",
                    MSG_TAB_AVAILABLE,
                    instanceId,
                    tabId,
                    ghostBounds.x,
                    ghostBounds.y);

            // Send via multicast
            byte[] buffer = message.getBytes();
            InetAddress group = InetAddress.getByName(DISCOVERY_GROUP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, DISCOVERY_PORT);
            discoverySocket.send(packet);

            logger.info("Offered tab to other processes: " + tabId);
            return tabId;
        } catch (Exception e) {
            logger.error("Error offering tab", e);
            return null;
        }
    }

    /**
     * Offers a tab to other processes and returns a future that completes when the
     * tab is accepted
     * 
     * @param ui          The UI component containing the entity to offer
     * @param ghostBounds The bounds of the ghost image
     * @param timeoutMs   Timeout in milliseconds (0 for no timeout)
     * @return A CompletableFuture that completes with true if the tab was accepted,
     *         false otherwise
     */
    public CompletableFuture<Boolean> offerTabAndWaitForAcceptance(MegaMekLabMainUI ui,
            Rectangle ghostBounds, long timeoutMs) {
        // Offer the tab and get the ID
        String tabId = offerTab(ui, ghostBounds);
        if (tabId == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Create a future for this tab
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        tabAcceptanceFutures.put(tabId, future);

        // Add a timeout if specified > 0
        if (timeoutMs > 0) {
            scheduledExecutor.schedule(() -> {
                CompletableFuture<Boolean> pendingFuture = tabAcceptanceFutures.remove(tabId);
                if (pendingFuture != null && !pendingFuture.isDone()) {
                    pendingFuture.complete(false);
                    logger.debug("Tab acceptance timed out: " + tabId);
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
        }

        return future;
    }

    /**
     * Listens for discovery messages from other instances
     */
    private void runDiscoveryListener() {
        byte[] buffer = new byte[1024];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                discoverySocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String[] parts = message.split("\\|");

                if (parts.length > 0) {
                    String type = parts[0];

                    if (MSG_INSTANCE_ANNOUNCE.equals(type)) {
                        handleInstanceAnnounce(parts, packet.getAddress());
                    } else if (MSG_INSTANCE_SHUTDOWN.equals(type)) {
                        handleInstanceShutdown(parts);
                    } else if (MSG_TAB_AVAILABLE.equals(type)) {
                        handleTabAvailable(parts, packet.getAddress());
                    } else if (MSG_TAB_ACCEPTED.equals(type)) {
                        handleTabAccepted(parts);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error in discovery listener", e);
                }
            } catch (Exception e) {
                logger.error("Unexpected error in discovery listener", e);
            }
        }
    }

    /**
     * Handles an instance announcement message
     */
    private void handleInstanceAnnounce(String[] parts, InetAddress address) {
        // Instance announcements just update our knowledge of other running instances
        // We don't need to take any action
        if (parts.length >= 6) {
            try {
                final String remoteId = parts[1];

                // Ignore our own announcements
                if (remoteId == instanceId) {
                    return;
                }

                // For future use - could track other instances in a map
                int remotePort = Integer.parseInt(parts[2]);
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);
                int width = Integer.parseInt(parts[5]);
                int height = Integer.parseInt(parts[6]);

                instancePorts.put(remoteId, remotePort);

                logger.debug("Received instance announcement from " + remoteId +
                        " at " + x + "," + y + " size " + width + "x" + height +
                        " on port " + remotePort);
            } catch (NumberFormatException e) {
                logger.error("Invalid instance announcement format", e);
            }
        }
    }

    /**
     * Handles an instance shutdown message
     */
    private void handleInstanceShutdown(String[] parts) {
        if (parts.length >= 2) {
            String remoteId = parts[1];
            instancePorts.remove(remoteId);
        }
    }

    /**
     * Handles a tab available message, checking if it's over our window
     */
    private void handleTabAvailable(String[] parts, InetAddress address) {
        if (parts.length >= 5) {
            try {
                final String remoteId = parts[1];

                // Ignore our own announcements
                if (remoteId == instanceId) {
                    return;
                }

                String tabId = parts[2];
                int ghostX = Integer.parseInt(parts[3]);
                int ghostY = Integer.parseInt(parts[4]);

                // Check if the tab ghost is over our window
                Point ghostPoint = new Point(ghostX, ghostY);
                if (windowBounds.contains(ghostPoint)) {
                    // Accept the tab
                    acceptTab(remoteId, tabId, address);
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid tab available format", e);
            }
        }
    }

    /**
     * Handles a tab accepted message
     */
    private void handleTabAccepted(String[] parts) {
        if (parts.length >= 3) {
            try {
                int remoteId = Integer.parseInt(parts[1]);
                String tabId = parts[2];

                // Complete the future if we're waiting for this tab
                CompletableFuture<Boolean> future = tabAcceptanceFutures.remove(tabId);
                if (future != null && !future.isDone()) {
                    future.complete(true);
                    logger.debug("Completed acceptance future based on broadcast: " + tabId);
                }

                logger.debug("Tab accepted by instance " + remoteId + ": " + tabId);
            } catch (NumberFormatException e) {
                logger.error("Invalid tab accepted format", e);
            }
        }
    }

    /**
     * Broadcasts a tab acceptance message to other instances
     * 
     * @param tabId
     */
    private void broadcastTabAccepted(String tabId) {
        try {
            // Create acceptance message
            String message = String.format("%s|%s|%s",
                    MSG_TAB_ACCEPTED,
                    instanceId,
                    tabId);

            // Send via multicast
            byte[] buffer = message.getBytes();
            InetAddress group = InetAddress.getByName(DISCOVERY_GROUP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, DISCOVERY_PORT);
            discoverySocket.send(packet);

            logger.debug("Broadcasted tab acceptance: " + tabId);
        } catch (Exception e) {
            logger.error("Error broadcasting tab acceptance", e);
        }
    }

    /**
     * Accepts a tab from another instance
     */
    private void acceptTab(String remoteId, String tabId, InetAddress address) {
        try {
            // Get the remote instance's port
            Integer remotePort = instancePorts.get(remoteId);
            if (remotePort == null) {
                logger.error("Unknown port for instance: " + remoteId);
                return;
            }
            // Send accept message via TCP
            try (Socket socket = new Socket(address, remotePort)) {
                OutputStream out = socket.getOutputStream();
                String acceptMessage = String.format("%s|%s|%s",
                        MSG_TAB_ACCEPT, instanceId, tabId);
                out.write(acceptMessage.getBytes());
                out.flush();

                // Wait for response with entity data
                InputStream in = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                // Parse the response
                String responseStr = response.toString();
                if (responseStr.startsWith("ENTITY|")) {
                    // Split into exactly 3 parts: "ENTITY", fileName, entityJson
                    String[] responseParts = responseStr.split("\\|", 3);
                    if (responseParts.length >= 3) {
                        String fileName = responseParts[1];
                        String entityJson = responseParts[2];

                        // Deserialize the entity
                        Entity entity = objectMapper.readValue(entityJson, Entity.class);

                        // Add to our UI with the original filename
                        SwingUtilities.invokeLater(() -> {
                            owner.addUnit(entity, fileName);
                        });

                        logger.info("Accepted tab from instance " + remoteId);

                        // After successful acceptance, broadcast it
                        broadcastTabAccepted(tabId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error accepting tab", e);
        }
    }

    /**
     * Listens for data transfer connections
     */
    private void runDataListener() {
        while (running) {
            try {
                Socket clientSocket = dataSocket.accept();

                // Handle client connection in a separate thread
                new Thread(() -> handleDataConnection(clientSocket)).start();
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }

    /**
     * Handles a data transfer connection
     */
    private void handleDataConnection(Socket clientSocket) {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            // Read request
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                String request = new String(buffer, 0, bytesRead);
                String[] parts = request.split("\\|");

                if (parts.length >= 3 && MSG_TAB_ACCEPT.equals(parts[0])) {
                    int remoteId = Integer.parseInt(parts[1]);
                    String tabId = parts[2];

                    // Check if we have this tab
                    TabEntity tabEntity = tabEntityRegistry.get(tabId);
                    if (tabEntity != null) {
                        // Serialize the entity directly to JSON
                        String entityJson = objectMapper.writeValueAsString(tabEntity.entity);

                        // Send response
                        String response = String.format("ENTITY|%s|%s", tabEntity.filename, entityJson);
                        out.write(response.getBytes());

                        tabEntityRegistry.remove(tabId);

                        // Broadcast the acceptance to other instances
                        broadcastTabAccepted(tabId);

                        // Complete the future for this tab
                        CompletableFuture<Boolean> future = tabAcceptanceFutures.remove(tabId);
                        if (future != null && !future.isDone()) {
                            future.complete(true);
                        }

                        logger.info("Sent entity data to instance " + remoteId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error handling data connection", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }
}