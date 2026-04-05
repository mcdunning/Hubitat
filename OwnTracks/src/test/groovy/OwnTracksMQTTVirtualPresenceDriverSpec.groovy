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

        // configure() sets fields from within the base class so assignment uses the
        // generated setter, not Script.setProperty (which routes to the Binding).
        driver.configure(
            settings:          [userId: 'john', deviceId: 'iphone', debugLogging: false],
            state:             [:],
            device:            [displayName: 'John iPhone'],
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

    // -------------------------------------------------------------------------
    // Presence logic
    // -------------------------------------------------------------------------

    def 'sets presence to present when topic matches and _type is location'() {
        given:
        givenMqttMessage('owntracks/john/iphone', '{"_type":"location","lat":51.5,"lon":-0.1}')

        when:
        driver.parse('rawEvent')

        then:
        sentEvents.any { it.name == 'presence' && it.value == 'present' }
    }

    def 'does not send presence event when _type is not location'() {
        given:
        givenMqttMessage('owntracks/john/iphone', '{"_type":"waypoint","desc":"Home"}')

        when:
        driver.parse('rawEvent')

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    def 'does not send presence event when topic does not match configured user and device'() {
        given:
        givenMqttMessage('owntracks/otheruser/otherdevice', '{"_type":"location","lat":51.5,"lon":-0.1}')

        when:
        driver.parse('rawEvent')

        then:
        !sentEvents.any { it.name == 'presence' }
    }

    def 'does not send presence event for transition message even when topic matches'() {
        given: 'OwnTracks transition/leave message — should eventually drive presence to not present'
        givenMqttMessage('owntracks/john/iphone', '{"_type":"transition","event":"leave","desc":"Home"}')

        when:
        driver.parse('rawEvent')

        then: 'no presence event fired — driver has no not-present logic yet'
        !sentEvents.any { it.name == 'presence' }
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

    def 'unsubscribe uses state.subscription_topic not settings'() {
        given:
        String unsubscribedTopic = null
        driver.configure(
            state: [subscription_topic: 'owntracks/+/+'],
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

    // -------------------------------------------------------------------------
    // State / event side-effects
    // -------------------------------------------------------------------------

    def 'always fires Subscription_topic_value event regardless of topic match'() {
        given:
        def payload = '{"_type":"location","lat":51.5,"lon":-0.1}'
        givenMqttMessage('owntracks/otheruser/otherdevice', payload)

        when:
        driver.parse('rawEvent')

        then:
        sentEvents.any { it.name == 'Subscription_topic_value' && it.value == payload }
    }

    def 'always updates state.Subscription_topic_value with latest payload'() {
        given:
        def payload = '{"_type":"waypoint","desc":"Office"}'
        givenMqttMessage('owntracks/john/iphone', payload)

        when:
        driver.parse('rawEvent')

        then:
        driver.state.Subscription_topic_value == payload
    }
}
