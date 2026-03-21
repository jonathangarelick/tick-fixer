package com.jonathang;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Tick Fixer for Mac",
	description = "Prevents macOS Wi-Fi power save from degrading tick quality by sending keepalive packets. "
		+ "Useful for tick manipulation activities like 2t woodcutting, 3t fishing, and prayer flicking.",
	tags = {"tick", "wifi", "macos", "latency", "jitter", "keepalive", "tick manipulation", "flicker", "flick"}
)
public class TickFixerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private TickFixerConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TickFixerOverlay overlay;

	@Getter
	private TickQualityTracker tickQualityTracker;

	@Getter
	private WifiKeepaliveThread keepaliveThread;

	@Provides
	TickFixerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickFixerConfig.class);
	}

	@Override
	protected void startUp()
	{
		migrateConfig();

		boolean isMacOS = System.getProperty("os.name", "").toLowerCase().contains("mac");

		if (!isMacOS)
		{
			log.info("Tick Fixer: Not running on macOS — keepalive thread disabled. "
				+ "Tick quality overlay is still active.");
		}

		tickQualityTracker = new TickQualityTracker(
			config.tickSampleSize(),
			config.tickQualityThresholdMs()
		);

		overlayManager.add(overlay);

		if (isMacOS)
		{
			startKeepalive();
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		stopKeepalive();
		tickQualityTracker = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (tickQualityTracker != null)
		{
			tickQualityTracker.recordTick();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && tickQualityTracker != null)
		{
			tickQualityTracker.reset();
		}

		if (keepaliveThread == null)
		{
			return;
		}

		if (config.onlyWhenLoggedIn())
		{
			if (event.getGameState() == GameState.LOGGED_IN)
			{
				keepaliveThread.unpause();
				log.debug("Keepalive resumed — logged in");
			}
			else if (event.getGameState() == GameState.LOGIN_SCREEN
				|| event.getGameState() == GameState.CONNECTION_LOST)
			{
				keepaliveThread.pause();
				log.debug("Keepalive paused — not logged in (state={})", event.getGameState());
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TickFixerConfig.GROUP_NAME.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "keepaliveIntervalMs":
				if (keepaliveThread != null)
				{
					keepaliveThread.setInterval(config.keepaliveIntervalMs());
					log.debug("Keepalive interval updated to {}ms", config.keepaliveIntervalMs());
				}
				break;

			case "targetHost":
			case "targetPort":
				if (keepaliveThread != null)
				{
					String host = config.targetHost();
					keepaliveThread.resolveAndSetTarget(host, config.targetPort(),
						() -> log.debug("Keepalive target updated"),
						() ->
						{
							keepaliveThread.pause();
							clientThread.invokeLater(() ->
								client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
									"Tick Fixer: Failed to resolve host '" + host + "'. Keepalive is paused until a valid host is set.",
									null));
						});
				}
				break;

			case "tickQualityThresholdMs":
				if (tickQualityTracker != null)
				{
					tickQualityTracker.setThresholdMs(config.tickQualityThresholdMs());
				}
				break;

			case "tickSampleSize":
				// Recreate the tracker with the new sample size
				if (tickQualityTracker != null)
				{
					tickQualityTracker = new TickQualityTracker(
						config.tickSampleSize(),
						config.tickQualityThresholdMs()
					);
				}
				break;
		}
	}

	private void migrateConfig()
	{
		String oldIpAddress = configManager.getConfiguration(TickFixerConfig.GROUP_NAME, "ipAddress");
		if (oldIpAddress != null)
		{
			log.info("Migrating config key 'ipAddress' -> 'targetHost' (value={})", oldIpAddress);
			configManager.setConfiguration(TickFixerConfig.GROUP_NAME, "targetHost", oldIpAddress);
			configManager.unsetConfiguration(TickFixerConfig.GROUP_NAME, "ipAddress");
		}

		// Clean up old v1 pingInterval key — not migrated since the new default (50ms) is preferred
		configManager.unsetConfiguration(TickFixerConfig.GROUP_NAME, "pingInterval");
	}

	private void startKeepalive()
	{
		if (keepaliveThread != null && keepaliveThread.isRunning())
		{
			return;
		}

		keepaliveThread = new WifiKeepaliveThread();
		keepaliveThread.setInterval(config.keepaliveIntervalMs());

		// If configured to only run when logged in, start paused
		if (config.onlyWhenLoggedIn() && client.getGameState() != GameState.LOGGED_IN)
		{
			keepaliveThread.pause();
		}

		keepaliveThread.start();

		// Resolve target asynchronously on the keepalive executor thread
		keepaliveThread.resolveAndSetTarget(config.targetHost(), config.targetPort(),
			null,
			() -> log.error("Failed to resolve keepalive target, keepalive will not send packets"));

		log.info("Wi-Fi keepalive started, resolving target asynchronously (interval={}ms)", config.keepaliveIntervalMs());
	}

	private void stopKeepalive()
	{
		if (keepaliveThread != null)
		{
			keepaliveThread.shutdown();
			keepaliveThread = null;
		}
	}
}
