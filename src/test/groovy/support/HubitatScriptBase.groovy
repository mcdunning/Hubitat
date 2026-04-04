package support

/**
 * Abstract base class injected into Hubitat driver scripts via GroovyShell's
 * CompilerConfiguration.scriptBaseClass. Provides stubs for the Hubitat
 * device API so driver logic can be tested outside the hub.
 *
 * Hubitat makes all preference values accessible as bare names (e.g. `debugLogging`).
 * propertyMissing() below replicates that by delegating to the `settings` map.
 */
abstract class HubitatScriptBase extends Script {

    Map settings = [:]
    Map state = [:]
    def interfaces
    def device
    def log = [debug: { msg -> }, info: { msg -> }, warn: { msg -> }, error: { msg -> }]

    /** Override in tests to capture sendEvent calls. */
    Closure sendEventCallback = { Map args -> }

    def sendEvent(Map args) {
        sendEventCallback(args)
    }

    void pauseExecution(long ms) { /* no-op */ }

    /**
     * Sets mock dependencies from within the base class so that field assignment
     * uses generated setters (not Script.setProperty, which routes to the Binding).
     * Call this from tests instead of direct property assignment on the Script instance.
     */
    void configure(Map config) {
        if (config.containsKey('settings'))          settings          = config.settings
        if (config.containsKey('state'))             state             = config.state
        if (config.containsKey('device'))            device            = config.device
        if (config.containsKey('interfaces'))        interfaces        = config.interfaces
        if (config.containsKey('sendEventCallback')) sendEventCallback = config.sendEventCallback
    }

    /** Mirrors Hubitat's implicit exposure of preferences as top-level properties. */
    def propertyMissing(String name) {
        if (settings?.containsKey(name)) return settings[name]
        throw new MissingPropertyException(name, this.class)
    }
}
