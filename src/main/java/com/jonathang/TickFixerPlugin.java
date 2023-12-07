package com.jonathang;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.OSType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@PluginDescriptor(name = "Tick Fixer for Mac")
public class TickFixerPlugin extends Plugin {
    private static final String[] CMD = {
            "/bin/zsh",
            "-c",
            "netstat -nr | awk '/default/ {print $2}' | head -n1"
    };
    private static final int MAX_FAILURES = 10;
    private static final int PING_INTERVAL = 200; // in milliseconds

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private ScheduledExecutorService exec;

    @Override
    protected void startUp() throws Exception {
        log.info("Tick Fixer for Mac started.");

        if (OSType.getOSType() != OSType.MacOS) {
            log.error("Operating system is not Mac. Terminating.");
            return;
        }

        InetAddress address = getDefaultGatewayAddress();
        if (address == null) return;

        log.debug("Default gateway is " + address);
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(this::pingGateway, 0, PING_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private InetAddress getDefaultGatewayAddress() throws IOException {
        log.debug("Getting default gateway address.");
        try (BufferedReader input = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(CMD).getInputStream()))) {
            final InetAddress address = InetAddress.getByName(input.readLine());

            if (address.isLoopbackAddress()) {
                log.error("Default gateway not found. Terminating.");
                return null;
            }

            return address;
        }
    }

    private void pingGateway() {
        if (failureCount.get() >= MAX_FAILURES) {
            log.error("Failed to ping default gateway 10 times. Terminating.");
            exec.shutdown();
            return;
        }

        try {
            InetAddress address = getDefaultGatewayAddress();
            boolean isReachable = address != null && address.isReachable(150);
            if (isReachable) {
                failureCount.set(0);
            } else {
                failureCount.getAndIncrement();
            }
        } catch (IOException e) {
            log.error("Error pinging gateway: " + e.getMessage());
            failureCount.getAndIncrement();
        }
    }

    @Override
    protected void shutDown() throws Exception {
        if (exec != null && !exec.isShutdown()) {
            exec.shutdown();
        }
        log.info("Tick Fixer for Mac stopped.");
    }
}
