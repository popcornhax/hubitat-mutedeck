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
 *  - OAuth is OPTIONAL:
 *      • If OAuth is enabled for this app in Hubitat Apps Code, an access_token
 *        will be generated and required for all requests.
 *      • If OAuth is not enabled, the local LAN endpoint is unauthenticated.
 *  - Optional Hubitat Cloud endpoint support is provided, but REQUIRES OAuth.
 *    Cloud access is disabled unless explicitly enabled by the user.
 *
 *  Installation Notes:
 *  -------------------
 *  1. Install this app via Apps Code in Hubitat.
 *  2. (Optional) Enable OAuth for this app in Apps Code.
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
    iconUrl:  "",
    iconX2Url:"",
    importUrl: "https://raw.githubusercontent.com/popcornhax/hubitat-mutedeck/apps/MuteDeckWebhookReceiver.groovy"
)

preferences { page(name: "mainPage") }

def mainPage() {
    dynamicPage(name: "mainPage", title: "MuteDeck Webhook Receiver", install: true, uninstall: true) {
        section("Device mapping (select existing Virtual Switches)") {
            input "muteSwitches",   "capability.switch", title: "Mute switches (ON=muted)", multiple: true, required: false
            input "callSwitches",   "capability.switch", title: "Call/Meeting switches (ON=in call)", multiple: true, required: false
            input "videoSwitches",  "capability.switch", title: "Video switches (ON=video active)", multiple: true, required: false
            input "shareSwitches",  "capability.switch", title: "Screen-share switches (ON=sharing)", multiple: true, required: false
            input "recordSwitches", "capability.switch", title: "Record switches (ON=recording)", multiple: true, required: false
        }

        section("Endpoint access") {
            input "useCloudEndpoint", "bool",
                title: "Allow external access via Hubitat Cloud endpoint (requires enabling OAuth support for app)",
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

            if (cloudMode && !state.accessToken) {
                paragraph "<b>Cloud endpoint requires OAuth.</b> Enable OAuth for this app in Apps Code (or re-add oauth: true) so an access token can be generated."
                paragraph "Local (LAN) mode can run unauthenticated without OAuth."
                return
            }

            def base = cloudMode ? safeCloudBaseUrl() : safeLocalBaseUrl()
            def urlInfo = buildWebhookUrl(base, cloudMode)

            paragraph "Paste into MuteDeck → Settings → Notifications → Webhook URL:"
            paragraph "<code>${urlInfo.url}</code>"

            if (urlInfo.mode == "oauth") {
                paragraph "Auth: oAuth access_token enabled.<br>Tip: Visit <a target=_new href='${base}/mutedeck?access_token=${state.accessToken}'>${base}/mutedeck?access_token=${state.accessToken}</a> to see last payload."
            } else if (urlInfo.mode == "open") {
                paragraph "Auth: none (LAN only).<br>Tip: Visit <a target=_new href='${base}/mutedeck'>${base}/mutedeck</a> to see last payload."
            } else {
                paragraph "<b>Action required:</b> Unable to compute endpoint base URL."
            }
        }
    }
}

def installed() { if (debugLogging) log.debug "Installed"; initialize() }
def updated()   { if (debugLogging) log.debug "Updated";   initialize() }
def initialize(){ initializeEndpointAuth() }

/**
 * Optional OAuth:
 * If OAuth is enabled for the app, createAccessToken() works. Otherwise it throws and we run open (LAN only).
 */
private void initializeEndpointAuth(Boolean forceNewAccessToken = false) {
    if (!state.accessToken || forceNewAccessToken) {
        try {
            createAccessToken()
        } catch (Exception ex) {
            state.remove("accessToken")
            if (debugLogging) log.debug "OAuth not enabled/available; LAN endpoint will be unauthenticated"
        }
    }
}

private String safeLocalBaseUrl() {
    try { return getFullLocalApiServerUrl() } catch (e) { return "" }
}

private String safeCloudBaseUrl() {
    try { return (getFullApiServerUrl()?.toString() ?: "") } catch (e) { return "" }
}

private Map buildWebhookUrl(String base, Boolean cloudMode) {
    boolean cm = (cloudMode ?: false)

    if (!base) return [mode: "error", url: "(Unable to compute endpoint base URL)"]

    if (state.accessToken) {
        return [mode: "oauth", url: "${base}/mutedeck?access_token=${state.accessToken}"]
    }

    // No token: allow only if NOT cloud mode
    if (!cm) return [mode: "open", url: "${base}/mutedeck"]

    // Should not happen because UI blocks it, but keep safe.
    return [mode: "error", url: "(Cloud mode requires OAuth token)"]
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

    applyStateToSwitches("mute",   payload.mute,   muteSwitches)
    applyStateToSwitches("call",   payload.call,   callSwitches)
    applyStateToSwitches("video",  payload.video,  videoSwitches)
    applyStateToSwitches("share",  payload.share,  shareSwitches)
    applyStateToSwitches("record", payload.record, recordSwitches)

    if (debugLogging) log.debug "Processed MuteDeck payload: ${payload}"
    render status: 200, contentType: "application/json", data: [ok: true]
}

private boolean authorizeRequest() {
    // If OAuth is enabled, Hubitat enforces access_token before calling handlers.
    // If OAuth isn't enabled, the endpoint is intentionally open (LAN mode only; UI blocks cloud).
    if (allowedIps) {
        def allowed = allowedIps.split(",").collect { it.trim() }.findAll { it }
        def src = (request?.getHeader("X-Forwarded-For") ?: request?.remoteAddr ?: "").toString()
        if (src.contains(",")) src = src.split(",")[0].trim()

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

private void applyStateToSwitches(String field, Object valueObj, def switches) {
    if (!switches) return
    def value = valueObj?.toString()
    if (!(value in ["active", "inactive"])) {
        if (debugLogging) log.debug "Field ${field}=${value} ignored (expected active/inactive)"
        return
    }
    switches.each { sw ->
        try { (value == "active") ? sw.on() : sw.off() }
        catch (e) { log.warn "Failed to set ${field} switch ${sw?.displayName}: ${e}" }
    }
}
