package com.jonathang;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(TickFixerConfig.GROUP_NAME)
public interface TickFixerConfig extends Config
{
	String GROUP_NAME = "tickFixerForMac";

	@ConfigSection(
		name = "Keepalive Settings",
		description = "Configure the Wi-Fi keepalive mechanism",
		position = 0
	)
	String keepaliveSection = "keepalive";

	@ConfigSection(
		name = "Diagnostics",
		description = "Tick quality monitoring",
		position = 1
	)
	String diagnosticsSection = "diagnostics";

	@ConfigItem(
		keyName = "keepaliveIntervalMs",
		name = "Keepalive Interval (ms)",
		description = "How often to send a keepalive UDP packet. Lower values keep the Wi-Fi radio more active but use slightly more power. 50ms is recommended.",
		position = 0,
		section = keepaliveSection
	)
	@Range(min = 10, max = 200)
	default int keepaliveIntervalMs()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "targetHost",
		name = "Target Host",
		description = "The host to send keepalive packets to. Default uses your default gateway. Use 'gateway' for auto-detection, or specify an IP address.",
		position = 1,
		section = keepaliveSection
	)
	default String targetHost()
	{
		return "gateway";
	}

	@ConfigItem(
		keyName = "targetPort",
		name = "Target Port",
		description = "UDP port to send keepalive packets to. Port 9 is the discard service (RFC 863) and won't generate ICMP responses on most routers.",
		position = 2,
		section = keepaliveSection
	)
	@Range(min = 1, max = 65535)
	default int targetPort()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "onlyWhenLoggedIn",
		name = "Only When Logged In",
		description = "Only send keepalive packets when logged into a game world.",
		position = 3,
		section = keepaliveSection
	)
	default boolean onlyWhenLoggedIn()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Tick Quality Overlay",
		description = "Display a small overlay showing tick quality percentage and keepalive status.",
		position = 0,
		section = diagnosticsSection
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tickSampleSize",
		name = "Tick Sample Size",
		description = "Number of recent ticks to use when calculating tick quality.",
		position = 1,
		section = diagnosticsSection
	)
	@Range(min = 10, max = 500)
	default int tickSampleSize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "tickQualityThresholdMs",
		name = "Quality Threshold (ms)",
		description = "Maximum deviation from 600ms for a tick to be considered 'good'. A tick arriving between 600-threshold and 600+threshold is counted as good quality.",
		position = 2,
		section = diagnosticsSection
	)
	@Range(min = 5, max = 100)
	default int tickQualityThresholdMs()
	{
		return 30;
	}
}
