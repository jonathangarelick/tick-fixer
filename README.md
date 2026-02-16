# Tick Fixer for Mac

A RuneLite plugin that prevents macOS Wi-Fi power save mode from degrading OSRS tick quality.

For background on the issue, see [this Reddit post](https://www.reddit.com/r/2007scape/comments/165nhch/huge_increase_in_tick_quality_on_mac_with_this/) and [this Stack Exchange discussion](https://apple.stackexchange.com/questions/357075/latency-spikes-on-wifi-power-saving-measure) on macOS Wi-Fi latency spikes.

## The Problem

macOS Wi-Fi power save mode (802.11 power save polling) causes the Wi-Fi radio to periodically sleep. When the radio sleeps, the access point buffers packets until the radio wakes and polls. This introduces **jitter** — variance in packet arrival timing — that degrades tick quality.

Instead of steady 600ms ticks, you get irregular intervals (500ms, 700ms, 580ms, 650ms). This makes tick manipulation unreliable for activities like 2t woodcutting, 3t fishing, and prayer flicking.

## The Solution

A lightweight background thread sends 1-byte UDP packets at 50ms intervals to your default gateway. This steady outbound traffic prevents the Wi-Fi firmware from entering power save mode. Packets never leave your local network — zero bandwidth impact, no firewall concerns.

## Features

- **Wi-Fi Keepalive** — Automatic UDP keepalive packets to prevent Wi-Fi power save (macOS only)
- **Tick Quality Overlay** — Real-time display of tick quality %, average tick duration, jitter, and last tick delta (works on all platforms)
- **Auto Gateway Detection** — Automatically finds your default gateway via `route -n get default`
- **Login-Aware** — Pauses keepalive when not logged in (configurable)
- **Fully Configurable** — Adjust interval, target host, overlay visibility, sample size, and quality threshold

## Configuration

### Keepalive Settings
| Setting | Default | Description |
|---------|---------|-------------|
| Keepalive Interval | 50ms | How often to send keepalive packets (10-200ms) |
| Target Host | `gateway` | Auto-detect gateway, or specify an IP |
| Target Port | 9 | UDP discard port (RFC 863) — no ICMP responses |
| Only When Logged In | true | Pause keepalive at login screen |

### Diagnostics
| Setting | Default | Description |
|---------|---------|-------------|
| Show Overlay | false | Display tick quality overlay |
| Tick Sample Size | 100 | Number of recent ticks to track |
| Quality Threshold | 30ms | Max deviation from 600ms for "good" tick |

## How It Works

The plugin detects macOS at startup and creates a `WifiKeepaliveThread` that:

1. Resolves the default gateway IP via `route -n get default`
2. Opens a `DatagramSocket`
3. Sends a single-byte UDP packet every 50ms (configurable) via a `ScheduledExecutorService`
4. Pauses when not logged in, resumes on login

The tick quality overlay (`TickQualityTracker`) works on all platforms and measures:
- **Quality %** — percentage of ticks within ±30ms of ideal 600ms
- **Average Tick** — mean tick duration over sample window
- **Jitter** — standard deviation of tick deltas
- **Last Tick** — most recent tick delta

## Support

Please create a [GitHub issue](https://github.com/jonathangarelick/tick-fixer/issues) if you need any support.

## License

BSD 2-Clause. See LICENSE file.
