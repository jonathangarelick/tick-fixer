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
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Override
    protected void startUp() {
        log.info("Tick Fixer v1.0.3 started"); // Remember to update build.gradle when changing version

        if (OSType.getOSType() != OSType.MacOS) {
            log.error("Operating system is not Mac. Terminating.");
            return;
        }

        startPing();
    }

    @Override
    protected void shutDown() {
        executor.shutdown();
        log.info("Tick Fixer stopped");
    }

    private String getDefaultGatewayAddress() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/zsh", "-c", "netstat -nr | awk '/default/ {print $2}' | head -n1");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();

            return InetAddress.getByName(output).getHostAddress();
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return null;
    }

    private void startPing() {
        String gatewayAddr = getDefaultGatewayAddress();

        if (gatewayAddr == null) {
            log.error("Can't find gateway address. Terminating");
            return;
        }

        log.debug("Gateway address: " + gatewayAddr);

        executor.scheduleAtFixedRate(() -> {
            if (failureCount.get() >= 3000) { // 10 minutes
                log.error("Failed to ping 3000 times. Terminating");
                shutDown();
                return;
            }

            try {
                Process process = Runtime.getRuntime().exec("/sbin/ping -c1 -W200 " + gatewayAddr);

                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                boolean timeout = false;
                while ((line = stdout.readLine()) != null) {
                    log.debug(line);
                    if (line.contains("timeout") || line.contains("0 packets received"))
                        timeout = true;
                }

                if (timeout) {
                    failureCount.getAndIncrement();
                    log.debug("Ping failed");
                }
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
}
