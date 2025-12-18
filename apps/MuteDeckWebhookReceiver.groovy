/*
 *  MuteDeck Webhook Receiver for Hubitat
 *
 *  Purpose:
 *  --------
 *  Receives webhook POSTs from MuteDeck (https://mutedeck.com) and translates
 *  meeting, mute, and video state changes into Hubitat device events.
 *
 *  This app is designed to bridge desktop meeting state (mute, in-call,
 *  camera on, screen share, recording) into Hubitat automations using
 *  standard Switch devices.
 *
 *  Common use cases:
 *   - Turn on a key light when camera is active
 *   - Indicate mute / in-call status on dashboards or LED indicators
 *   - Drive Rule Machine automations from real-time meeting state
 *
 *  Authentication:
 *  ---------------
 *  - By default, the webhook endpoint is intended for use on a trusted home LAN.
 *  - Optional Hubitat Cloud endpoint support is provided.
 *    Cloud access is disabled unless explicitly enabled by the user.
 *
 *  Installation Notes:
 *  -------------------
 *  1. Install this app via Apps Code in Hubitat.
 *  2. Enable OAuth for this app in Apps Code.
 *  3. Configure desired Switch mappings.
 *  4. Copy the generated webhook URL into MuteDeck settings.
 *
 *  Author:
 *  -------
 *  popcornhax
 *
 *  Contact / Support:
 *  ------------------
 *  Issues, questions, or feature requests:
 *    - GitHub: https://github.com/popcornhax/hubitat-mutedeck
 *
 *  License:
 *  --------
 *  Free for personal use. No warranty expressed or implied.
 *
 */

import groovy.json.JsonSlurper

definition(
    name: "MuteDeck Webhook Receiver",
    namespace: "popcornhax",
    author: "You",
    description: "Receives MuteDeck webhook POSTs and updates selected Hubitat devices",
    category: "Convenience",
    oauth: true,
    iconUrl:  "",
    iconX2Url:"",
    importUrl: "https://raw.githubusercontent.com/popcornhax/hubitat-mutedeck/main/apps/MuteDeckWebhookReceiver.groovy"
)

preferences { page(name: "mainPage") }

def mainPage() {
    dynamicPage(name: "mainPage", title: "MuteDeck Webhook Receiver", install: true, uninstall: true) {
        section("Device mapping (select existing Switches)") {
            input "muteSwitches",   "capability.switch", title: "Mute switches (ON=unmuted)", multiple: true, required: false
            input "callSwitches",   "capability.switch", title: "Call/Meeting switches (ON=in call)", multiple: true, required: false
            input "videoSwitches",  "capability.switch", title: "Video switches (ON=video active)", multiple: true, required: false
            input "shareSwitches",  "capability.switch", title: "Screen-share switches (ON=sharing)", multiple: true, required: false
            input "recordSwitches", "capability.switch", title: "Record switches (ON=recording)", multiple: true, required: false
        }

        section("Endpoint access") {
            input "useCloudEndpoint", "bool",
                title: "Allow external access via Hubitat Cloud endpoint",
                defaultValue: false,
                required: false,
                submitOnChange: true
        }

        section("IP Restriction") {
            input "allowedIps", "string",
                title: "Allowed source IPs (optional, comma-separated). Note: cloud calls appear from Hubitat relay IPs.",
                required: false
        }

        section("Logging") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: false
        }

        section("Webhook URL") {
            initializeEndpointAuth()

            Boolean cloudMode = (settings?.useCloudEndpoint as Boolean) ?: false
            def token = state.accessToken

            if (!token) {
                paragraph "<b>OAuth token missing.</b> Ensure OAuth is enabled for this app in Apps Code, then open this app once to generate an access token."
                return
            }

            def base = cloudMode ? safeCloudBaseUrl() : safeLocalBaseUrl()
            def urlInfo = buildWebhookUrl(base)

            paragraph "Paste into MuteDeck → Settings → Notifications → Enable Webook → Webhook URL"
            paragraph "<code>${urlInfo.url}</code>"

            paragraph "Tip: Visit <a target=_new href='${base}/mutedeck?access_token=${token}'>${base}/mutedeck?access_token=${token}</a> to see last payload."
        }
    }
}

def installed() { if (debugLogging) log.debug "Installed"; initialize() }
def updated()   { if (debugLogging) log.debug "Updated";   initialize() }
def initialize(){ initializeEndpointAuth() }

private void initializeEndpointAuth(Boolean forceNewAccessToken = false) {
    if (!state.accessToken || forceNewAccessToken) {
        createAccessToken()
    }
}

private String safeLocalBaseUrl() {
    try { return getFullLocalApiServerUrl() } catch (e) { return "" }
}

private String safeCloudBaseUrl() {
    try { return (getFullApiServerUrl()?.toString() ?: "") } catch (e) { return "" }
}

private Map buildWebhookUrl(String base) {
    if (!base) return [mode: "error", url: "(Unable to compute endpoint base URL)"]
    if (!state.accessToken) return [mode: "error", url: "(OAuth token missing - open this app to generate token)"]
    return [mode: "oauth", url: "${base}/mutedeck?access_token=${state.accessToken}"]
}

mappings {
    path("/mutedeck") {
        action: [ POST: "handleMuteDeckWebhook", GET: "health" ]
    }
}

def health() {
    if (!authorizeRequest()) return
    render status: 200, contentType: "application/json", data: [
        ok: true,
        appId: app?.id,
        lastSeen: state.lastSeen,
        lastPayload: state.lastPayload
    ]
}

def handleMuteDeckWebhook() {
    if (!authorizeRequest()) return

    Map payload = parseJsonPayload()
    if (!payload) {
        render status: 400, contentType: "application/json", data: [ok: false, error: "no JSON payload"]
        return
    }

    state.lastSeen = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", location?.timeZone ?: TimeZone.getTimeZone("UTC"))
    state.lastPayload = payload

    // Always update call state first
    applyStateToSwitches("call",   payload.call,   callSwitches)

    // Guardrail: Only allow other switches to be ON when a call is active.
    // This avoids "stuck" mute state when MuteDeck switches to controlling the system mic outside a meeting.
    def inCall = (payload.call?.toString() == "active")
    if (!inCall) {
        if (debugLogging) log.debug "No active call; forcing non-call switches OFF (mute/video/share/record)"
        forceSwitchesOff(muteSwitches)
        forceSwitchesOff(videoSwitches)
        forceSwitchesOff(shareSwitches)
        forceSwitchesOff(recordSwitches)
    } else {
        applyStateToSwitches("mute",   payload.mute,   muteSwitches)
        applyStateToSwitches("video",  payload.video,  videoSwitches)
        applyStateToSwitches("share",  payload.share,  shareSwitches)
        applyStateToSwitches("record", payload.record, recordSwitches)
    }

    if (debugLogging) log.debug "Processed MuteDeck payload: ${payload}"
    render status: 200, contentType: "application/json", data: [ok: true]
}


private boolean authorizeRequest() {
    if (allowedIps) {
        def allowed = allowedIps.split(",").collect { it.trim() }.findAll { it }
        def src = (request?.getHeader("X-Forwarded-For") ?: request?.remoteAddr ?: "").toString()
        // X-Forwarded-For can be a list; take first hop
        if (src.contains(",")) src = src.split(",")[0].trim()

        // Note: With cloud mode enabled, src will usually be a Hubitat relay IP, not your original client.
        if (src && allowed && !allowed.contains(src)) {
            log.warn "Webhook rejected: source IP ${src} not in allowlist"
            render status: 403, contentType: "application/json", data: [ok: false, error: "forbidden"]
            return false
        }
    }
    return true
}

private Map parseJsonPayload() {
    try { if (request?.JSON instanceof Map) return request.JSON as Map } catch (ignored) {}
    def body = request?.body
    if (!body) return [:]
    try { return (new JsonSlurper().parseText(body) as Map) ?: [:] }
    catch (e) { log.warn "Failed to parse JSON body: ${e}"; return [:] }
}

private void forceSwitchesOff(def switches) {
    if (!switches) return
    switches.each { sw ->
        try { sw.off() }
        catch (e) { log.warn "Failed to force switch OFF ${sw?.displayName}: ${e}" }
    }
}

private void applyStateToSwitches(String field, Object valueObj, def switches) {
    if (!switches) return
    def value = valueObj?.toString()
    if (!(value in ["active", "inactive"])) {
        if (debugLogging) log.debug "Field ${field}=${value} ignored (expected active/inactive)"
        return
    }
    if (field == "mute") {
        // Mute logic is inverted: active = unmuted
        value = (value == "active") ? "inactive" : "active"
    }
    switches.each { sw ->
        try { (value == "active") ? sw.on() : sw.off() }
        catch (e) { log.warn "Failed to set ${field} switch ${sw?.displayName}: ${e}" }
    }
}
