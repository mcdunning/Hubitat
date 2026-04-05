/**
 * OwnTracks MQTT Link
 *
 * MIT License
 *
 * Copyright (c) 2020 license@mydevbox.com
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
import groovy.transform.Field

public static String version() { return "v1.0.0" }

definition(
    name: "OwnTracks MQTT Link",
    namespace: "mdunning",
    author: "Matt Dunning",
    description: "An app to process messages from the MQTT Link Driver",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
)

preferences {
    page(name: "devicePage", nextPage: "capabilitiesPage", uninstall: true) {
        section("Select the hub devices that MQTT Link should monitor and control.", hideable: false) {
            input (
                name: "selectedDevices", 
                type: "capability.*", 
                title: "Select devices", 
                multiple: true,
                required: true,
                submitOnChange: false
            )
        }
        section ("<h3>Specify MQTT Link Driver device</h3>") {
            paragraph "The MQTT Link Driver must be set up prior to the MQTT Link app otherwise the driver will not show up here."
            input (
                name: "mqttLink", 
                type: "capability.notification", 
                title: "Notify this driver", 
                required: true, 
                multiple: false,
                submitOnChange: false
            )
        }
        section("Debug Settings") {
            input("debugLogging", "bool", title: "Enable debug logging", required: false, default:false) 
        }
    }
    page(name: "capabilitiesPage", install: true)
}

def capabilitiesPage() {
    def deprecatedCapabilities = ["Actuator","Beacon","Bridge","Bulb","Button","Garage Door Control",
                                  "Indicator","Light","Lock Only","Music Player","Outlet","Polling","Relay Switch",
                                  "Sensor","Shock Sensor","Thermostat Setpoint","Thermostat","Touch Sensor",
                                  "Configuration","Refresh"]
    dynamicPage(name: "capabilitiesPage") {        
        section ("<h2>Specify Exposed Capabilities per Device</h2>") {
            paragraph """<style>.pill {border-radius:4px;background-color:#337ab7;color:#fff;padding:10px 15px;
                font-weight:bold;} .label {font-family: Helvetica,Arial,sans-serif;background-color: #5bc0de;
                display: inline;padding: .2em .6em .3em;font-size: 75%;font-weight: 700;line-height: 1;color: 
                #fff;text-align: center;white-space: nowrap;vertical-align: baseline;
                border-radius: .25em; }</style>""".stripMargin()

            // Build normalized list of selected device names 
            def selectedList = []
            selectedDevices.each {
                device -> selectedList.add(normalizeId(device))
            }
            state.selectedList = selectedList

            // Remove deselected device capabilities
            settings.each { setting ->
                if (setting.value.class == java.util.ArrayList) {
                    if (!state.selectedList.contains(setting.key)) {
                        app.removeSetting(setting.key)
                    }
                }
            }
            
            // Build normalized list of selected device names 
            def selectedLookup = [:]
            selectedDevices.each {
                device -> selectedLookup.put(normalizeId(device), device.getDisplayName())
            }
            state.selectedLookup = selectedLookup
            
            // List selected devices with capabilities chooser
            selectedDevices.sort{x -> x.getDisplayName()}.each { device ->

                def selectedCapabilities = []
                def deviceCapabilities = device.getCapabilities()
    
                deviceCapabilities.each { capability ->
                    if (!deprecatedCapabilities.contains(capability.getName())) {
                        selectedCapabilities.add(capability.getName())
                    }
                }
                
                def normalizeId = normalizeId(device)
                
                paragraph "<div class=\"pill\">${device.getDisplayName()}</div>"

                input (
                    name: normalizeId, 
                    type: "enum",
                    title: "",
                    options: selectedCapabilities,
                    multiple: true,
                    submitOnChange: false
                )
                paragraph "<div><strong style=\"font-size: 85%;\">Topic </strong><div class=\"label\">${getTopicPrefix()}${normalizeId}</div></div><hr />"
            }
        }
    }
}