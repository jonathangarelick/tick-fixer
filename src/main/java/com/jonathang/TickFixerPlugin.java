package com.jonathang;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.OSType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@PluginDescriptor(name = "Tick Fixer for Mac")
public class TickFixerPlugin extends Plugin {
    private static final int MAX_FAILURES = 100;
    private static final int PING_INTERVAL = 200; // in milliseconds
    private static final int GATEWAY_PORT = 80;
    private static final int GATEWAY_TIMEOUT = 300;

    private final AtomicInteger failureCount = new AtomicInteger(0);

    private ScheduledExecutorService scheduler;

    @Override
    protected void startUp() {
        String version = getVersionFromResource();
        log.info("Tick Fixer {} started", version != null ? version : "(version unknown)");

        if (OSType.getOSType() != OSType.MacOS) {
            log.error("Operating system is not Mac. Terminating.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            final InetAddress gatewayAddress = getDefaultGatewayAddress();
            log.debug("Default gateway is {}", gatewayAddress.getHostAddress());
            scheduler.scheduleAtFixedRate(() -> pingGateway(gatewayAddress), 0, PING_INTERVAL, TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            log.error("A fatal error has occurred.", e);
        }
    }

    /**
     * Attempts to retrieve the plugin version from a "version.txt" resource file.
     *
     * @return the version string if found, or null if the resource is not found or an error occurs
     */
    public static String getVersionFromResource() {
        String version = null;

        try (InputStream stream = TickFixerPlugin.class.getResourceAsStream("/version.txt")) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(stream);
                version = properties.getProperty("version");
            } else {
                log.error("Warning: Version resource (version.txt) not found.");
            }
        } catch (IOException e) {
            log.error("Error reading version resource: " + e.getMessage());
        }

        return version;
    }

    /**
     * Retrieves the default gateway address of the system.
     *
     * <p>This method executes a shell command to fetch the default gateway address and returns it as an {@link InetAddress}.
     * The shell command is specifically designed for macOS systems with /bin/zsh and netstat installed, and might not work correctly on other systems.
     *
     * @return An {@link InetAddress} representing the default gateway address.
     * @throws IOException if an I/O error occurs when executing the shell command,
     *                     if the command output is empty or not in the expected format,
     *                     or if the default gateway address is a loopback address.
     */
    private InetAddress getDefaultGatewayAddress() throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{
                "/bin/zsh",
                "-c",
                "netstat -nr | awk '/default/ {print $2}' | head -n1"
        });
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = input.readLine();
            if (line == null) {
                throw new IOException("The command output is empty.");
            }
            InetAddress address = InetAddress.getByName(line);
            if (address.isLoopbackAddress()) {
                throw new IOException("The default gateway address is a loopback address.");
            }
            return address;
        } catch (UnknownHostException e) {
            throw new IOException("The host is unknown.", e);
        }
    }

    /**
     * Attempts to check if a gateway is reachable by establishing a TCP connection.
     *
     * @param address the InetAddress representing the gateway to check
     * @return true if a TCP connection can be established, false otherwise.
     */
    public boolean isGatewayReachable(InetAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, GATEWAY_PORT), GATEWAY_TIMEOUT);
            return true;
        } catch (IOException e) {
            log.error("Error pinging gateway.", e);
            return false;
        }
    }

    /**
     * Pings the default gateway and updates the failure count.
     *
     * <p>This method attempts to ping the default gateway. If the gateway is reachable, it resets the failure count to 0.
     * If the gateway is not reachable or if an I/O error occurs, it increments the failure count.
     *
     * <p>If the failure count reaches a maximum limit (MAX_FAILURES), it logs an error message and shuts down the scheduler.
     *
     * @param address the InetAddress representing the default gateway to be pinged.
     */
    private void pingGateway(final InetAddress address) {
        if (failureCount.get() >= MAX_FAILURES) {
            log.error("Failed to ping default gateway {} times. Terminating.", MAX_FAILURES);
            scheduler.shutdown();
            return;
        }

        if (isGatewayReachable(address)) {
            failureCount.set(0);
        } else {
            failureCount.getAndIncrement();
        }
    }

    @Override
    protected void shutDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        log.info("Tick Fixer stopped");
    }
}
