package com.jonathang;

import lombok.Setter;

/**
 * Measures tick quality by tracking the interval between consecutive GameTick events.
 * Uses a ring buffer of recent tick deltas and calculates the percentage that fall
 * within an acceptable threshold of the ideal 600ms.
 */
public class TickQualityTracker
{
	private static final long IDEAL_TICK_MS = 600;
	private static final int DISREGARD_TICKS = 15;

	private final long[] tickDeltas;
	private final int capacity;
	private int head = 0;
	private int count = 0;
	private long lastTickNanos = 0;
	@Setter
	private int thresholdMs;
	private int disregardRemaining = DISREGARD_TICKS;

	public TickQualityTracker(int sampleSize, int thresholdMs)
	{
		this.capacity = sampleSize;
		this.tickDeltas = new long[sampleSize];
		this.thresholdMs = thresholdMs;
	}

	/**
	 * Call this on every GameTick event.
	 *
	 * <p>The first DISREGARD_TICKS ticks after a reset are skipped to allow
	 * the game to stabilize after login or world hop.
	 */
	public void recordTick()
	{
		long now = System.nanoTime();
		if (disregardRemaining > 0)
		{
			disregardRemaining--;
			lastTickNanos = now;
			return;
		}
		if (lastTickNanos != 0)
		{
			long deltaMs = (now - lastTickNanos) / 1_000_000L;
			tickDeltas[head] = deltaMs;
			head = (head + 1) % capacity;
			if (count < capacity)
			{
				count++;
			}
		}
		lastTickNanos = now;
	}

	/**
	 * Returns true if the tracker is still in the disregard period after a reset.
	 */
	public boolean isWaiting()
	{
		return disregardRemaining > 0;
	}

	/**
	 * Returns tick quality as a percentage (0-100).
	 *
	 * <p>A tick is "good" if its delta is within thresholdMs of IDEAL_TICK_MS.
	 */
	public double getTickQuality()
	{
		if (count == 0)
		{
			return 100.0;
		}

		int goodTicks = 0;
		for (int i = 0; i < count; i++)
		{
			long delta = tickDeltas[i];
			if (Math.abs(delta - IDEAL_TICK_MS) <= thresholdMs)
			{
				goodTicks++;
			}
		}

		return (goodTicks * 100.0) / count;
	}

	/**
	 * Returns the average tick delta in ms over the sample window.
	 */
	public double getAverageTickMs()
	{
		if (count == 0)
		{
			return IDEAL_TICK_MS;
		}

		long sum = 0;
		for (int i = 0; i < count; i++)
		{
			sum += tickDeltas[i];
		}
		return (double) sum / count;
	}

	/**
	 * Returns the standard deviation of tick deltas (jitter) in ms.
	 */
	public double getJitterMs()
	{
		if (count < 2)
		{
			return 0.0;
		}

		double mean = getAverageTickMs();
		double sumSqDiff = 0;
		for (int i = 0; i < count; i++)
		{
			double diff = tickDeltas[i] - mean;
			sumSqDiff += diff * diff;
		}
		return Math.sqrt(sumSqDiff / count);
	}

	/**
	 * Returns the most recent tick delta in ms, or -1 if no data.
	 */
	public long getLastTickDeltaMs()
	{
		if (count == 0)
		{
			return -1;
		}
		int lastIdx = (head - 1 + capacity) % capacity;
		return tickDeltas[lastIdx];
	}

	/**
	 * Reset all collected data and restart the disregard period.
	 */
	public void reset()
	{
		head = 0;
		count = 0;
		lastTickNanos = 0;
		disregardRemaining = DISREGARD_TICKS;
	}

}
