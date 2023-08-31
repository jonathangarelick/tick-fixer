package com.jonathang;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@PluginDescriptor(
        name = "Tick Fixer for Mac"
)
public class TickFixerPlugin extends Plugin {
    private static final String[] cmd = {
            "/bin/zsh",
            "-c",
            "netstat -nr | awk '/default/ {print $2}' | head -n1"
    };

    private final AtomicBoolean isGatewayReachable = new AtomicBoolean(false);
    private final AtomicInteger failureCount = new AtomicInteger(0);

	private ScheduledExecutorService exec;

    @Inject
    private Client client;

    @Inject
    private TickFixerConfig config;

    @Override
    protected void startUp() throws Exception {
        log.info("Tick Fixer for Mac started.");

        if (!SystemUtils.IS_OS_MAC) {
            log.error("Operating system is not Mac. Terminating.");
            return;
        }

        log.info("Getting default gateway address.");
        Process pr = Runtime.getRuntime().exec(cmd);

        BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        final InetAddress address = InetAddress.getByName(input.readLine());

        if (address.isLoopbackAddress()) {
            log.error("Default gateway not found. Terminating.");
            return;
        }

        log.info("Default gateway is " + address);

		exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(() -> {
            if (failureCount.get() >= 10) {
                log.error("Failed to ping default gateway 10 times. Terminating.");
                exec.shutdown();
            }

            try {
                isGatewayReachable.set(address.isReachable(150));
                log.info("Tick at " + System.currentTimeMillis());

                if (isGatewayReachable.get())
                    failureCount.set(0);
                else
                    failureCount.getAndIncrement();
            } catch (IOException e) {
                log.error(e.getMessage());
                failureCount.getAndIncrement();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutDown() throws Exception {
        exec.shutdown();
        log.info("Tick Fixer for Mac stopped.");
    }

    @Provides
    TickFixerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TickFixerConfig.class);
    }
}
