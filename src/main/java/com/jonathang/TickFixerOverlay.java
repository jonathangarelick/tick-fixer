package com.jonathang;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class TickFixerOverlay extends OverlayPanel
{
	private final TickFixerPlugin plugin;
	private final TickFixerConfig config;

	@Inject
	public TickFixerOverlay(TickFixerPlugin plugin, TickFixerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		TickQualityTracker tracker = plugin.getTickQualityTracker();
		if (tracker == null)
		{
			return null;
		}

		double quality = tracker.getTickQuality();
		double avgTick = tracker.getAverageTickMs();
		double jitter = tracker.getJitterMs();
		long lastDelta = tracker.getLastTickDeltaMs();

		// Color code the quality percentage
		Color qualityColor;
		if (quality >= 95)
		{
			qualityColor = Color.GREEN;
		}
		else if (quality >= 80)
		{
			qualityColor = Color.YELLOW;
		}
		else if (quality >= 60)
		{
			qualityColor = Color.ORANGE;
		}
		else
		{
			qualityColor = Color.RED;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Tick Fixer")
			.color(Color.WHITE)
			.build());

		if (tracker.isWaiting())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status")
				.right("Waiting...")
				.rightColor(Color.YELLOW)
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Tick Quality")
				.right(String.format("%.1f%%", quality))
				.rightColor(qualityColor)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Avg Tick")
				.right(String.format("%.0fms", avgTick))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Jitter")
				.right(String.format("%.1fms", jitter))
				.rightColor(jitter > 30 ? Color.ORANGE : Color.WHITE)
				.build());

			if (lastDelta >= 0)
			{
				Color deltaColor = Math.abs(lastDelta - 600) <= config.tickQualityThresholdMs()
					? Color.GREEN : Color.RED;
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Last Tick")
					.right(lastDelta + "ms")
					.rightColor(deltaColor)
					.build());
			}
		}

		addKeepaliveStatus();

		return super.render(graphics);
	}

	private void addKeepaliveStatus()
	{
		WifiKeepaliveThread keepalive = plugin.getKeepaliveThread();
		String keepaliveStatus;
		Color keepaliveColor;
		if (keepalive == null || !keepalive.isRunning())
		{
			keepaliveStatus = "OFF";
			keepaliveColor = Color.GRAY;
		}
		else if (keepalive.isPaused())
		{
			keepaliveStatus = "PAUSED";
			keepaliveColor = Color.YELLOW;
		}
		else
		{
			keepaliveStatus = "ACTIVE";
			keepaliveColor = Color.GREEN;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Keepalive")
			.right(keepaliveStatus)
			.rightColor(keepaliveColor)
			.build());
	}
}
