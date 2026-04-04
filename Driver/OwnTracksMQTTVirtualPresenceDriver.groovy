/**
 *  MQTT Link Driver
 *
 * MIT License
 *
 * Copyright (c) 2025 Matt Dunning
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

@CompileStatic(TypeCheckingMode.SKIP)
static String version() { return 'v0.1.0' }
static String rootTopic() { return 'hubitat' }

metadata
{
    definition(
        name: 'OwnTracks MQTT Virtual Presence Driver',
        namespace: 'mdunning',
        author: 'Matt Dunning',
        description: 'Driver to subscribe to an MQTT topic and, if needed, provide a two-way communication')

    {
        capability 'Notification'
        capability 'PresenceSensor'

        preferences
        {
            input(
                name: 'brokerIp',
                type: 'string',
                title: 'MQTT Broker IP Address',
                description: 'e.g. 192.168.1.200',
                required: true,
                displayDuringSetup: true)
            input(
                name: 'brokerPort',
                type: 'string',
                title: 'MQTT Broker Port',
                description: 'e.g. 1883',
                required: true,
                displayDuringSetup: true,
                default: '1883')
            input(
                name: 'deviceId',
                type: 'string',
                title: 'Device ID',
                description: 'The id assigned to the device to monitor',
                required: true,
                displayDuringSetup: true
            )
            input(
                name: 'userId',
                type: 'string',
                title: 'User ID',
                description: 'The id of the user associated with the device to monitor',
                required: true,
                displayDuringSetup: true)
            input(
                name: 'subscription_topics',
                type: 'string',
                title: 'Topic',
                description: 'The topic(s) that will be subscribed to and monitored ' +
                    'for this device e.g. owntracks/+/+. To register for multiple ' +
                    "topics enter each one separated by a ','",
                required: true,
                displayDuringSetup: true
            )
            input(
                name: 'brokerUser',
                type: 'string',
                title: 'MQTT Broker Username',
                description: 'User to log into the MQTT broker e.g. mqtt_user',
                required: false,
                displayDuringSetup: true)
            input(
                name: 'brokerPassword',
                type: 'password',
                title: 'MQTT Broker Password',
                description: 'e.g. ^L85er1Z7g&%2En!',
                required: false,
                displayDuringSetup: true)
            input(
                name: 'debugLogging',
                type: 'bool',
                title: 'Enable debug logging',
                required: false,
                default: false)
        }

        // Provided for broker setup and troubleshooting
        command 'publish',
        [
            [
                name:'topic*',
                type:'STRING',
                title:'test',
                description:'Topic'
            ],
            [
                name:'message',
                type:'STRING',
                description:'Message'
            ]
        ]
        command 'subscribe',
        [
            [
                name:'topic*',
                type:'STRING',
                description:'Topic'
            ]
        ]
        command 'unsubscribe',
        [
            [
                name:'topic*',
                type:'STRING',
                description:'Topic'
            ]
        ]
        command 'connect'
        command 'disconnect'
    }
}

void initialize() {
    debug('Initializing driver...')
    try {
        debug('Creating Connection...')
        interfaces.mqtt.connect(brokerUri(),
                                'hubitat-Hubitat', //add device ID to make unique
                                settings?.brokerUser,
                                settings?.brokerPassword)

        // delay for connection
        pauseExecution(1000)

        // subscribe to the topic of this driver
        debug('Subscribing to topics...')
        subscribe()
        debug('Connected...')
        connected()
    } catch (IOException | IllegalArgumentException e) {
        error("[d:initialize] ${e}")
    }
}

// reconnect on saved changes
void updated() {
    disconnect()
    connect()
}

// ========================================================
// MQTT COMMANDS
// ========================================================

void publish(String topic, String payload) {
    publishMqtt(topic, payload)
}

void subscribe() {
    if (!mqttConnected()) {
        connect()
    }

    debug("[d:subscribe] full topic: ${settings?.subscription_topics}")
    String[] topics = settings?.subscription_topics.split(',')
    debug("[d:subscribe] topics: ${topics}")
    topics.each { topic -> interfaces.mqtt.subscribe(topic) }
    state.subscription_topic = settings?.subscription_topics
}

void unsubscribe() {
    if (!mqttConnected()) {
        connect()
    }

    debug("[d:unsubscribe] full topic: ${settings?.subscription_topic}")
    interfaces.mqtt.unsubscribe(settings?.subscription_topic)
}

void connect() {
    initialize()
}

void disconnect() {
    try {
        unsubscribe()
        interfaces.mqtt.disconnect()
        disconnected()
    } catch (e) {
        warn("Disconnection from broker failed: ${e.message}")
        if (interfaces.mqtt.isConnected()) {
            connected()
        }
    }
}

// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
void parse(String event) {
    Map message = interfaces.mqtt.parseMessage(event)

    debug("[d:parse] Received MQTT message: topic = ${message.topic}")
    debug("[d:parse] Looking for topic ${mqttTopic()}")

    String topic = message.topic
    if (topic.indexOf(mqttTopic()) != -1) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        Object payload = jsonSlurper.parseText(message.payload)
        debug("[d:parse] json payload: ${payload}")

        // For OwnTracks location messages, set presence to present
        if (payload._type == 'location') {
            sendEvent(name: 'presence', value: 'present', descriptionText: "${device.displayName} is present")
        }
    }

    state.Subscription_topic_value = message.payload
    return sendEvent(name: 'Subscription_topic_value', value: message.payload, displayed: true)
}

void mqttClientStatus(Map status) {
    debug("[d:mqttClientStatus] status: ${status}")
}

void publishMqtt(String topic, String payload, Integer qos = 0, Boolean retained = false) {
    if (!mqttConnected()) {
        debug('[d:publishMqtt] not connected')
        initialize()
    }

    String pubTopic = "${topic}"

    try {
        interfaces.mqtt.publish("${pubTopic}", payload, qos, retained)
        debug("[d:publishMqtt] topic: ${pubTopic} payload: ${payload}")
    } catch (IOException e) {
        error("[d:publishMqtt] Unable to publish message: ${e}")
    }
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

void connected() {
    debug('[d:connected] Connected to broker')
    String connectionStateValue = 'connected'
    state.connectionState = connectionStateValue
    sendEvent(name: 'connectionState', value: connectionStateValue)
}

void disconnected() {
    debug('[d:disconnected] Disconnected from broker')
    String connectionStateValue = 'disconnected'
    state.connectionState = connectionStateValue
    sendEvent(name: 'connectionState', value: connectionStateValue)
}

// ========================================================
// HELPERS
// ========================================================

String normalize(String name) {
    return name.replaceAll('[^a-zA-Z0-9]+', '-').toLowerCase()
}

String brokerUri() {
    return "ssl://${settings?.brokerIp}:${settings?.brokerPort}"
}

String hubId() {
    Object hub = location.hubs[0]
    String hubNameNormalized = normalize(hub.name)
    return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}

boolean mqttConnected() {
    return interfaces.mqtt.isConnected()
}

String mqttTopic() {
    return "owntracks/${settings?.userId}/${settings?.deviceId}"
}

// ========================================================
// LOGGING
// ========================================================

void debug(String msg) {
    if (debugLogging) {
        log.debug msg
    }
}

void info(String msg) {
    log.info msg
}

void warn(String msg) {
    log.warn msg
}

void error(String msg) {
    log.error msg
}
