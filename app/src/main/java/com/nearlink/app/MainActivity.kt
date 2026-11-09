package com.nearlink.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.Payload
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val serviceId = "com.nearlink.app.DIRECT"
    private val strategy = Strategy.P2P_CLUSTER
    private val endpointNames = mutableMapOf<String, String>()
    private val pendingMessages = mutableMapOf<String, String>()
    private var connectedEndpoint: String? = null
    private var localName = "NearLink-${Build.MODEL.take(18)}"

    private fun emit(event: String, data: JSONObject = JSONObject()) {
        if (!::webView.isInitialized) return
        val payload = JSONObject().put("event", event).put("data", data)
        runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('nearlink-native', {detail:${JSONObject.quote(payload.toString())}}));",
                null
            )
        }
    }

    private fun requiredPermissions(): Array<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_CONNECT
            p += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            p += Manifest.permission.ACCESS_COARSE_LOCATION
            if (Build.VERSION.SDK_INT >= 29) p += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissions(action: () -> Unit) {
        if (hasPermissions()) action()
        else ActivityCompat.requestPermissions(this, requiredPermissions(), 4102)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            emit("permission", JSONObject().put("granted", true))
        } else if (requestCode == 4102) emit("permission", JSONObject().put("granted", false))
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Pair with ${info.endpointName}?")
                    .setMessage("Compare this verification code on both phones:\n\n${info.authenticationDigits}\n\nOnly accept if the codes match.")
                    .setPositiveButton("Accept") { _, _ -> client.acceptConnection(endpointId, payloadCallback) }
                    .setNegativeButton("Reject") { _, _ -> client.rejectConnection(endpointId) }
                    .setCancelable(false).show()
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoint = endpointId
                emit("connected", JSONObject().put("endpointId", endpointId).put("name", endpointNames[endpointId] ?: "Nearby device"))
            } else {
                emit("connection-failed", JSONObject().put("status", result.status.statusCode))
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpoint == endpointId) connectedEndpoint = null
            emit("disconnected", JSONObject().put("endpointId", endpointId))
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            emit("device-found", JSONObject().put("endpointId", endpointId).put("name", info.endpointName))
        }
        override fun onEndpointLost(endpointId: String) {
            emit("device-lost", JSONObject().put("endpointId", endpointId))
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val raw = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
            try {
                val packet = JSONObject(raw)
                when (packet.optString("type")) {
                    "message" -> {
                        val id = packet.getString("id")
                        emit("message-received", JSONObject().put("id", id).put("text", packet.getString("text")).put("time", packet.optLong("time")))
                        sendPacket(endpointId, JSONObject().put("type", "ack").put("id", id))
                    }
                    "ack" -> {
                        val id = packet.getString("id")
                        pendingMessages.remove(id)
                        emit("message-delivered", JSONObject().put("id", id))
                    }
                }
            } catch (_: Exception) {
                emit("protocol-error", JSONObject().put("message", "Invalid message packet"))
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.FAILURE) emit("transfer-failed")
        }
    }

    private fun sendPacket(endpointId: String, packet: JSONObject) {
        client.sendPayload(endpointId, Payload.fromBytes(packet.toString().toByteArray(Charsets.UTF_8)))
    }

    inner class NearLinkBridge {
        @JavascriptInterface fun startDiscovery() {
            ensurePermissions {
                client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
                    .addOnSuccessListener { emit("discovery-started") }
                    .addOnFailureListener { emit("native-error", JSONObject().put("message", it.localizedMessage ?: "Discovery failed")) }
                client.startAdvertising(localName, serviceId, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
                    .addOnFailureListener { emit("native-error", JSONObject().put("message", it.localizedMessage ?: "Advertising failed")) }
            }
        }
        @JavascriptInterface fun stopDiscovery() {
            client.stopDiscovery()
            client.stopAdvertising()
            emit("discovery-stopped")
        }
        @JavascriptInterface fun connect(endpointId: String) {
            ensurePermissions {
                client.requestConnection(localName, endpointId, lifecycle)
                    .addOnFailureListener { emit("native-error", JSONObject().put("message", it.localizedMessage ?: "Connection request failed")) }
            }
        }
        @JavascriptInterface fun sendMessage(text: String, id: String) {
            val endpoint = connectedEndpoint
            if (endpoint == null) {
                emit("message-failed", JSONObject().put("id", id).put("reason", "No connected device"))
                return
            }
            pendingMessages[id] = endpoint
            sendPacket(endpoint, JSONObject().put("type", "message").put("id", id).put("text", text).put("time", System.currentTimeMillis()))
            emit("message-sent", JSONObject().put("id", id))
        }
        @JavascriptInterface fun disconnect() {
            connectedEndpoint?.let { client.disconnectFromEndpoint(it) }
            connectedEndpoint = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 18, 29)
        window.navigationBarColor = android.graphics.Color.rgb(12, 18, 29)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(12, 18, 29))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            addJavascriptInterface(NearLinkBridge(), "NearLinkNative")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                    request?.url?.let { assetLoader.shouldInterceptRequest(it) }
            }
        }
        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/nearlink/index.html")
    }

    @Deprecated("Deprecated in Android, retained for compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) { webView.stopLoading(); webView.destroy() }
        super.onDestroy()
    }
}
