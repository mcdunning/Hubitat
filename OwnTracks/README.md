# OwnTracks MQTT Presence

Hubitat driver for tracking device presence using [OwnTracks](https://owntracks.org/) via MQTT.

## Overview

This project integrates OwnTracks location data with Hubitat Elevation by subscribing to an MQTT broker. When a tracked device enters or leaves a defined region (e.g. "home"), the corresponding Hubitat virtual presence sensor is updated automatically.

## Driver — OwnTracks MQTT Virtual Presence Driver

[Drivers/OwnTracksMQTTVirtualPresenceDriver.groovy](Drivers/OwnTracksMQTTVirtualPresenceDriver.groovy)

A Hubitat driver that connects directly to your MQTT broker and subscribes to OwnTracks topics. It processes two types of OwnTracks payloads:

- **Location messages** — checks the `inregions` field to determine if the device is home
- **Transition messages** — responds to `enter`/`leave` events for the "home" region

**Capabilities:** `PresenceSensor`, `Notification`

**Configuration:**

| Setting | Description |
|---|---|
| MQTT Broker IP | IP address of your MQTT broker |
| MQTT Broker Port | Port (default: 1883) |
| MQTT Broker Username | Broker login username |
| MQTT Broker Password | Broker login password |
| User ID | OwnTracks user ID (e.g. `john`) |
| Device ID | OwnTracks device ID (e.g. `iphone`) |
| Topic | MQTT topic(s) to subscribe to (e.g. `owntracks/+/+`) — separate multiple topics with commas |
| Debug Logging | Enable verbose logging |

## Setup

1. Install the **OwnTracks MQTT Virtual Presence Driver** in Hubitat under **Drivers Code**
2. Create a new virtual device using that driver
3. Configure the driver with your MQTT broker details and OwnTracks user/device IDs
4. In OwnTracks, configure a region named `home` — presence will update automatically on enter/leave

## Requirements

- Hubitat Elevation hub
- MQTT broker (e.g. Mosquitto) accessible from the hub
- OwnTracks app configured on your mobile device(s)

## License

MIT License — Copyright (c) 2025 Matt Dunning
