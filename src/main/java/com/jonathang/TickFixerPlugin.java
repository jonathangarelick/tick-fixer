package com.jonathang;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.OSType;
import org.apache.commons.validator.routines.InetAddressValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/*
    Contributions:
    - Molorius: createExecutor and shutdownExecutor functions
*/
@Slf4j
@PluginDescriptor(name = "Tick Fixer for Mac")
public class TickFixerPlugin extends Plugin {
    // Configuration
    @Inject
    private TickFixerConfig config;

    private int pingInterval;
    private String targetAddress;

    // Failure management
    private Instant lastSuccessfulPing;

    // Thread management
    private ScheduledExecutorService executor;

    @Provides
    TickFixerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(TickFixerConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals(TickFixerConfig.GROUP_NAME)) {
            updateConfig();
        }
    }

    private void updateConfig() {
        targetAddress = config.ipAddress();
        pingInterval = config.pingInterval();
    }

    private void createExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
    }

    @Override
    protected void startUp() {
        log.info("Tick Fixer v1.0.4 started"); // Remember to update build.gradle when changing version

        if (OSType.getOSType() != OSType.MacOS) {
            log.error("Operating system is not Mac. Terminating.");
            return;
        }

        updateConfig();

        if (targetAddress != null) {
            log.debug("Found player-configured IP address {} ", targetAddress);
        }

        createExecutor();
        lastSuccessfulPing = Instant.now();
        schedulePingTask();
    }

    private void shutdownExecutor() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Override
    protected void shutDown() {
        shutdownExecutor();
        log.info("Tick Fixer stopped");
    }

    private String getDefaultGatewayAddress() {
        Process process = null;
        try {
            process = new ProcessBuilder("/bin/zsh", "-c", "netstat -nr -f inet | awk '/default/ {print $2}' | head -n1").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader
                        .lines()
                        .findFirst()
                        .map(output -> {
                            try {
                                return InetAddress.getByName(output).getHostAddress();
                            } catch (UnknownHostException e) {
                                log.error(e.getMessage());
                                return null;
                            }
                        })
                        .orElse(null);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private String getTargetAddress() {
        if (InetAddressValidator.getInstance().isValidInet4Address(targetAddress)) {
            return targetAddress;
        }

        targetAddress = getDefaultGatewayAddress();
        if (InetAddressValidator.getInstance().isValidInet4Address(targetAddress)) {
            return targetAddress;
        }

        throw new IllegalStateException("No valid target address found");
    }

    private boolean ping(String targetAddress) {
        Process process = null;
        try {
            process = new ProcessBuilder("/sbin/ping", "-c", "1", targetAddress).start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private void schedulePingTask() {
        log.debug("Scheduling ping task");

        executor.scheduleAtFixedRate(() -> {
            if (Duration.between(lastSuccessfulPing, Instant.now()).compareTo(Duration.ofMinutes(5)) > 0) {
                log.error("No successful ping in the last 5 minutes. Shutting down");
                shutDown();
                return;
            }

            // Warning: the compiler does not enforce try-catch here
            try {
                if (ping(getTargetAddress())) {
                    lastSuccessfulPing = Instant.now();
                }
            } catch (IllegalStateException e) {
                log.error(e.getMessage());
            }
        }, 0, pingInterval, TimeUnit.MILLISECONDS);
    }
}
