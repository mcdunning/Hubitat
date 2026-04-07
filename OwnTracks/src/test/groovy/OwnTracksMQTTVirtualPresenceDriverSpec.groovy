import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification
import support.HubitatScriptBase

class OwnTracksMQTTVirtualPresenceDriverSpec extends Specification {

    Script driver
    List<Map> sentEvents

    def setup() {
        sentEvents = []

        def config = new CompilerConfiguration(scriptBaseClass: HubitatScriptBase.name)
        def shell = new GroovyShell(this.class.classLoader, new Binding(), config)
        def projectDir = System.getProperty('project.dir', '.')
        driver = shell.parse(new File(projectDir, 'OwnTracks/Drivers/OwnTracksMQTTVirtualPresenceDriver.groovy'))

        driver.configure(
            settings:          [userId: 'john', deviceId: 'iphone', debugLogging: false],
            state:             [:],
            device:            [displayName: 'John iPhone', currentValue: { String attr -> null }],
            sendEventCallback: { Map args -> sentEvents << args }
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void givenMqttMessage(String topic, String payload) {
        driver.configure(interfaces: [
            mqtt: [
                parseMessage: { String event -> [topic: topic, payload: payload] }
            ]
        ])
    }

    private Object parseJson(String json) {
        new JsonSlurper().parseText(json)
    }

    // -------------------------------------------------------------------------
    // handleLocationPayload — invoked when topic does NOT end with 'Event'
    // -------------------------------------------------------------------------

    def 'location: inregions contains home → present'() {
        when:
        driver.handleLocationPayload(parseJson('{"_type":"location","inregions":["home","work"]}'))

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'present' }
    }

    def 'location: inregions exists but home is not in it → not present'() {
        when:
        driver.handleLocationPayload(parseJson('{"_type":"location","inregions":["work"]}'))

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'not present' }
    }

    def 'location: inregions absent → no presence event'() {
        when:
        driver.handleLocationPayload(parseJson('{"_type":"location","lat":51.5,"lon":-0.1}'))

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    def 'location: non-location _type → no presence event'() {
        when:
        driver.handleLocationPayload(parseJson('{"_type":"waypoint","desc":"Home"}'))

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    // -------------------------------------------------------------------------
    // handleTransitionPayload — invoked when topic ends with 'Event'
    // -------------------------------------------------------------------------

    def 'transition: enter home → present'() {
        when:
        driver.handleTransitionPayload(parseJson('{"_type":"transition","event":"enter","desc":"home"}'))

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'present' }
    }

    def 'transition: leave home → not present'() {
        when:
        driver.handleTransitionPayload(parseJson('{"_type":"transition","event":"leave","desc":"home"}'))

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'not present' }
    }

    def 'transition: non-home region → no presence event'() {
        when:
        driver.handleTransitionPayload(parseJson('{"_type":"transition","event":"enter","desc":"work"}'))

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    def 'transition: non-transition _type → no presence event'() {
        when:
        driver.handleTransitionPayload(parseJson('{"_type":"location","lat":51.5,"lon":-0.1}'))

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    // -------------------------------------------------------------------------
    // parse() — topic routing and side-effects
    // -------------------------------------------------------------------------

    def 'parse: topic ending with Event routes to transition handler'() {
        given:
        givenMqttMessage('owntracks/john/iphone/event', '{"_type":"transition","event":"leave","desc":"home"}')

        when:
        driver.parse('rawEvent')

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'not present' }
    }

    def 'parse: topic not ending with Event routes to location handler'() {
        given:
        givenMqttMessage('owntracks/john/iphone', '{"_type":"location","inregions":["home"]}')

        when:
        driver.parse('rawEvent')

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'present' }
    }

    def 'parse: topic not matching configured user and device → no presence event'() {
        given:
        givenMqttMessage('owntracks/otheruser/otherdevice', '{"_type":"location","inregions":["home"]}')

        when:
        driver.parse('rawEvent')

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    def 'parse: always fires lastMessage event regardless of topic match'() {
        given:
        def payload = '{"_type":"location","inregions":["home"]}'
        givenMqttMessage('owntracks/otheruser/otherdevice', payload)

        when:
        driver.parse('rawEvent')

        then:
        sentEvents.any { it.name == 'lastMessage' && it.value == payload }
    }

    def 'parse: always updates state.lastMessage'() {
        given:
        def payload = '{"_type":"location","inregions":[]}'
        givenMqttMessage('owntracks/john/iphone', payload)

        when:
        driver.parse('rawEvent')

        then:
        driver.state.lastMessage == payload
    }

    // -------------------------------------------------------------------------
    // mqttClientStatus
    // -------------------------------------------------------------------------

    def 'mqttClientStatus accepts a String status'() {
        when:
        driver.mqttClientStatus('Status: Connection succeeded')

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // unsubscribe
    // -------------------------------------------------------------------------

    def 'unsubscribe uses state.subscribedTopics not settings'() {
        given:
        String unsubscribedTopic = null
        driver.configure(
            state: [subscribedTopics: 'owntracks/+/+'],
            interfaces: [
                mqtt: [
                    isConnected:  { true },
                    unsubscribe:  { String t -> unsubscribedTopic = t }
                ]
            ]
        )

        when:
        driver.unsubscribe()

        then:
        unsubscribedTopic == 'owntracks/+/+'
    }
}
