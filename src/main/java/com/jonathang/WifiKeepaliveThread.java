package com.jonathang;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends small UDP keepalive packets at a configurable interval to prevent the
 * macOS Wi-Fi radio from entering 802.11 power save mode.
 *
 * <p>When the Wi-Fi radio enters power save, the access point buffers packets destined
 * for the client until the radio wakes and polls. This introduces jitter in packet
 * delivery timing, which degrades tick quality in OSRS since the server sends update
 * packets on a strict 600ms cadence.
 *
 * <p>By maintaining a steady stream of small outbound UDP packets, we signal to the
 * Wi-Fi firmware that the connection is active, preventing it from entering the
 * power save polling state.
 */
@Slf4j
public class WifiKeepaliveThread
{
	/** Single-byte payload — minimal packet to keep the radio awake */
	private static final byte[] KEEPALIVE_PAYLOAD = new byte[]{0x00};

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean paused = new AtomicBoolean(false);
	private final AtomicInteger intervalMs = new AtomicInteger(50);
	private final AtomicReference<InetAddress> targetAddress = new AtomicReference<>();
	private final AtomicInteger targetPort = new AtomicInteger(9);

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> scheduledTask;
	private DatagramSocket socket;
	private long totalPacketsSent = 0;
	private long totalErrors = 0;

	/**
	 * Attempt to detect the default gateway IP by parsing the output of `route` or `netstat`.
	 * Falls back to the router's common default if detection fails.
	 */
	public static InetAddress detectDefaultGateway()
	{
		try
		{
			// macOS: `route -n get default` prints the gateway
			Process process = Runtime.getRuntime().exec(new String[]{"route", "-n", "get", "default"});
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					line = line.trim();
					if (line.startsWith("gateway:"))
					{
						String gateway = line.substring("gateway:".length()).trim();
						log.info("Detected default gateway: {}", gateway);
						return InetAddress.getByName(gateway);
					}
				}
			}
			process.waitFor();
		}
		catch (Exception e)
		{
			log.warn("Failed to detect default gateway via route command", e);
		}

		// Fallback: try common gateway addresses
		try
		{
			InetAddress candidate = InetAddress.getByName("192.168.1.1");
			if (candidate.isReachable(500))
			{
				log.info("Using fallback gateway: {}", candidate.getHostAddress());
				return candidate;
			}
		}
		catch (Exception e)
		{
			// ignore
		}

		try
		{
			// Last resort: use Google DNS. Packets leave the local network, but it's reliable.
			log.info("Using fallback target: 8.8.8.8");
			return InetAddress.getByName("8.8.8.8");
		}
		catch (Exception e)
		{
			log.error("Failed to resolve any keepalive target", e);
			return null;
		}
	}

	/**
	 * Resolve the target address from the config string.
	 *
	 * <p>"gateway" triggers auto-detection; anything else is treated as a hostname/IP.
	 */
	public static InetAddress resolveTarget(String configValue)
	{
		if (configValue == null || configValue.trim().isEmpty() || configValue.trim().equalsIgnoreCase("gateway"))
		{
			return detectDefaultGateway();
		}

		try
		{
			return InetAddress.getByName(configValue.trim());
		}
		catch (Exception e)
		{
			log.error("Failed to resolve target host '{}', falling back to gateway detection", configValue, e);
			return detectDefaultGateway();
		}
	}

	public void configure(InetAddress target, int port, int interval)
	{
		this.targetAddress.set(target);
		this.targetPort.set(port);
		this.intervalMs.set(interval);
	}

	public void setInterval(int ms)
	{
		this.intervalMs.set(Math.max(10, Math.min(200, ms)));
		reschedule();
	}

	public void setTarget(InetAddress address, int port)
	{
		this.targetAddress.set(address);
		this.targetPort.set(port);
	}

	public void pause()
	{
		paused.set(true);
	}

	public void unpause()
	{
		paused.set(false);
	}

	public boolean isPaused()
	{
		return paused.get();
	}

	public boolean isRunning()
	{
		return running.get();
	}

	public void start()
	{
		if (running.get())
		{
			return;
		}

		try
		{
			socket = new DatagramSocket();
			socket.setSoTimeout(100);
		}
		catch (Exception e)
		{
			log.error("Failed to create keepalive socket", e);
			return;
		}

		executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "wifi-keepalive");
			t.setDaemon(true);
			return t;
		});

		running.set(true);
		schedule();

		log.info("Wi-Fi keepalive started (interval={}ms, target={}:{})",
			intervalMs.get(),
			targetAddress.get() != null ? targetAddress.get().getHostAddress() : "null",
			targetPort.get());
	}

	public void shutdown()
	{
		if (!running.compareAndSet(true, false))
		{
			return;
		}

		if (scheduledTask != null)
		{
			scheduledTask.cancel(false);
			scheduledTask = null;
		}

		if (executor != null)
		{
			executor.shutdown();
			try
			{
				if (!executor.awaitTermination(2, TimeUnit.SECONDS))
				{
					executor.shutdownNow();
				}
			}
			catch (InterruptedException e)
			{
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			executor = null;
		}

		if (socket != null)
		{
			socket.close();
			socket = null;
		}

		log.info("Wi-Fi keepalive stopped (sent {} packets, {} errors)", totalPacketsSent, totalErrors);
	}

	private void schedule()
	{
		scheduledTask = executor.scheduleAtFixedRate(
			this::sendKeepalive,
			0,
			intervalMs.get(),
			TimeUnit.MILLISECONDS
		);
	}

	private void reschedule()
	{
		if (!running.get() || executor == null)
		{
			return;
		}

		if (scheduledTask != null)
		{
			scheduledTask.cancel(false);
		}

		schedule();
	}

	private void sendKeepalive()
	{
		if (paused.get())
		{
			return;
		}

		InetAddress target = targetAddress.get();
		if (target == null)
		{
			return;
		}

		try
		{
			DatagramPacket packet = new DatagramPacket(
				KEEPALIVE_PAYLOAD,
				KEEPALIVE_PAYLOAD.length,
				target,
				targetPort.get()
			);
			socket.send(packet);
			totalPacketsSent++;
		}
		catch (Exception e)
		{
			totalErrors++;
			if (totalErrors % 100 == 1)
			{
				log.debug("Keepalive send error (total errors: {}): {}", totalErrors, e.getMessage());
			}
		}
	}
}
