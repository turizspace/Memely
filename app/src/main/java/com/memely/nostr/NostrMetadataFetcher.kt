package com.memely.nostr

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancel
import org.json.JSONArray

object NostrMetadataFetcher {

    suspend fun fetchMetadataAndRelays(
        pubkey: String,
        relayPool: RelayPool
    ): Pair<MetadataParser.UserMetadata?, List<String>> {

        val relayList = mutableSetOf<String>()
        var metadata: MetadataParser.UserMetadata? = null

        val metadataReq =
            """["REQ","meta-${pubkey.take(6)}",{"kinds":[0],"authors":["$pubkey"]}]"""
        val relaysReq =
            """["REQ","relays-${pubkey.take(6)}",{"kinds":[10002],"authors":["$pubkey"]}]"""

        println("📡 Requesting kind=0 and kind=10002 for pubkey: $pubkey")

        // Broadcast the requests to all connected relays
        relayPool.broadcast(metadataReq)
        relayPool.broadcast(relaysReq)

        // Collect and parse incoming responses
        withTimeoutOrNull(8000) {
            relayPool.incomingMessagesFlow.collect { msg ->
                try {
                    val arr = JSONArray(msg)
                    if (arr.optString(0) != "EVENT") return@collect
                    val eventObj = arr.getJSONObject(2)
                    val kind = eventObj.optInt("kind")
                    val content = eventObj.optString("content")

                    when (kind) {
                        Constants.KIND_METADATA -> {
                            println("🟢 Got kind=0 metadata event")
                            val parsed = MetadataParser.parseMetadata(content)
                            if (parsed != null) {
                                metadata = parsed
                                println("✅ Parsed metadata: name=${parsed.name}, nip05=${parsed.nip05}, pic=${parsed.picture}")
                            } else {
                                println("⚠️ Failed to parse metadata JSON: $content")
                            }
                        }

                        10002 -> {
                            println("🟣 Got kind=10002 relay list")
                            relayList.addAll(MetadataParser.parseRelayList(content))
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Metadata parse error: ${e.message}")
                }

                if (metadata != null) {
                    cancel() // Stop collecting once metadata is found
                }
            }
        }

        if (metadata == null) println("⚠️ No kind=0 metadata found for $pubkey")
        if (relayList.isEmpty()) println("⚠️ No kind=10002 relay list found for $pubkey")

        return Pair(metadata, relayList.toList())
    }
}
