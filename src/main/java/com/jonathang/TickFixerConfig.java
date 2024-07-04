package com.jonathang;

import net.runelite.client.config.*;

@ConfigGroup(TickFixerConfig.GROUP_NAME)
public interface TickFixerConfig extends Config {
    String GROUP_NAME = "tickFixerForMac";

    @ConfigSection(
            name = "Danger Zone",
            description = "Do not modify this unless you know what you're doing!",
            position = 0
    )
    String dangerZone = "dangerZone";

    @ConfigItem(
            keyName = "ipAddress",
            name = "Override IP address",
            description = "The address must follow IPv4 format",
            position = 0,
            section = dangerZone
    )
    default String ipAddress() {
        return "";
    }

    @ConfigItem(
            keyName = "pingInterval",
            name = "Ping interval",
            description = "The frequency at which the plugin should ping the IP address",
            position = 1,
            section = dangerZone
    )
    @Range(min = 100)
    @Units(Units.MILLISECONDS)
    default int pingInterval() {
        return 200;
    }
}
