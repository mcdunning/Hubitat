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
import groovy.json.JsonOutput

public static String version() { return "v0.1.0" }
public static String rootTopic() { return "hubitat" }

metadata 
{
    definition(name: "OwnTracks MQTT Virtual Presence Driver", namespace: "mdunning", author: "Matt Dunning", description: "Driver to subscribe to an MQTT topic and, if needed, provide a two-way communication")
    {
        capability "Notification"
        capability "PresenceSensor"
        
        preferences 
        {
            input( name: "brokerIp", type: "string", title: "MQTT Broker IP Address", description: "e.g. 192.168.1.200", required: true, displayDuringSetup: true )
            input( name: "brokerPort", type: "string", title: "MQTT Broker Port", description: "e.g. 1883", required: true, displayDuringSetup: true, default: "1883" )
    		input( name: "deviceId", type: "string", title: "Device ID", description: "The id assigned to the device to monitor",  required: true, displayDuringSetup: true )
    		input( name: "userId", type: "string", title: "User ID", description: "The id of the user associated with teh device to monitor", required: true, displayDuringSetup: true )
    		input( name: "subscription_topics", type: "string", title: "Topic", description: "The topic(s) that will be subscribed to and monitored for this device e.g. owntracks/+/+.  To register for multiple topics enter each one separted by a ','", required: true, displayDuringSetup: true )
    		input( name: "brokerUser", type: "string", title: "MQTT Broker Username", description: "User to log into the MQTT broker e.g. mqtt_user", required: false, displayDuringSetup: true )
			input( name: "brokerPassword", type: "password", title: "MQTT Broker Password", description: "e.g. ^L85er1Z7g&%2En!", required: false, displayDuringSetup: true )
			input( name: "debugLogging", type: "bool", title: "Enable debug logging", required: false, default: false )
    	}
		
		// Provided for broker setup and troubleshooting
		command "publish", [[name:"topic*",type:"STRING", title:"test",description:"Topic"],[name:"message",type:"STRING", description:"Message"]]
		command "subscribe",[[name:"topic*",type:"STRING", description:"Topic"]]
		command "unsubscribe",[[name:"topic*",type:"STRING", description:"Topic"]]
		command "connect"
		command "disconnect"
	}
}

void initialize() {
    debug("Initializing driver...")
    try {   
        debug("Creating Connection...");
        interfaces.mqtt.connect(getBrokerUri(),
                                "hubitat-Hubitat", //add device ID to make unique
                                settings?.brokerUser, 
                                settings?.brokerPassword);
       
        // delay for connection
        pauseExecution(1000)
        
        // subscribe to the topic of this driver
        debug("Subscribing to topics...")
        subscribe()
        debug("Connected...")
        connected()
        
    } catch(Exception e) {
        error("[d:initialize] ${e}")
    }
}

// reconnect on saved changes
def updated() {
    disconnect()
    connect()
}

// ========================================================
// MQTT COMMANDS
// ========================================================

def publish(topic, payload) {
    // NoOp - subscribe only
}

def subscribe() {
    if (notMqttConnected()) {
        connect()
    }

    debug("[d:subscribe] full topic: ${settings?.subscription_topics}")
    String[] topics = settings?.subscription_topics.split(",")
    debug("[d:subscribe] topics: ${topics}")
    topics.each{interfaces.mqtt.subscribe(it)}
    state.subscription_topic = settings?.subscription_topics
}

def unsubscribe() {
    if (notMqttConnected()) {
        connect()
    }
    
    debug("[d:unsubscribe] full topic: ${settings?.subscription_topic}")
    interfaces.mqtt.unsubscribe(settings?.subscription_topic)
}

def connect() {
    initialize()
}

def disconnect() {
    try {
        unsubscribe()
        interfaces.mqtt.disconnect()   
        disconnected()
    } catch(e) {
        warn("Disconnection from broker failed", ${e.message})
        if (interfaces.mqtt.isConnected()) connected()
    }
}


// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)  
    
    debug("[d:parse] Received MQTT message: topic = ${message.topic}")
    debug("[d:parse] Looking for topic ${getMQTTTopic()}");
    
    def topic = message.topic;
    if (topic.indexOf(getMQTTTopic()) != -1) {
        def jsonSlurper = new JsonSlurper()
        def payload = jsonSlurper.parseText(message.payload)
        debug("[d:parse] json payload: ${payload}")
    }
    
   if (message.payload == settings?.switch_on_value) {
       sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
   }
    
    if (message.payload == settings?.switch_off_value) {
        sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
    }
    
    
    state.Subscription_topic_value = message.payload
    return sendEvent(name: "Subscription_topic_value", value: message.payload, displayed: true)
}

def mqttClientStatus(status) {
    debug("[d:mqttClientStatus] status: ${status}")
}

def publishMqtt(topic, payload, qos = 0, retained = false) {
    if (notMqttConnected()) {
        debug("[d:publishMqtt] not connected")
        initialize()
    }
    
    def pubTopic = "${topic}"

    try {
        interfaces.mqtt.publish("${pubTopic}", payload, qos, retained)
        debug("[d:publishMqtt] topic: ${pubTopic} payload: ${payload}")
        
    } catch (Exception e) {
        error("[d:publishMqtt] Unable to publish message: ${e}")
    }
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

def connected() {
    debug("[d:connected] Connected to broker")
    state.connectionState = "connected"
    sendEvent (name: "connectionState", value: "connected")
}

def disconnected() {
    debug("[d:disconnected] Disconnected from broker")
    state.connectionState = "disconnected"
    sendEvent (name: "connectionState", value: "disconnected")
}


// ========================================================
// HELPERS
// ========================================================

def normalize(name) {
    return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def getBrokerUri() {        
    return "ssl://${settings?.brokerIp}:${settings?.brokerPort}"
}

def getHubId() {
    def hub = location.hubs[0]
    def hubNameNormalized = normalize(hub.name)
    return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}

def mqttConnected() {
    return interfaces.mqtt.isConnected()
}

def notMqttConnected() {
    return !mqttConnected()
}

def getMQTTTopic() {
    return "owntracks/${settings?.userId}/${settings?.deviceId}"
}

// ========================================================
// LOGGING
// ========================================================

def debug(msg) {
	if (debugLogging) {
    	log.debug msg
    }
}

def info(msg) {
    log.info msg
}

def warn(msg) {
    log.warn msg
}

def error(msg) {
    log.error msg
}