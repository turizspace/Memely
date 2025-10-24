package com.memely.data.models

data class NostrEvent(
	val id: String,
	val pubkey: String,
	val created_at: Long,
	val kind: Int,
	val tags: List<List<String>> = emptyList(),
	val content: String,
	val sig: String
)

