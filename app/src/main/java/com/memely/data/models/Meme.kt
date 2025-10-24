package com.memely.data.models

data class Meme(
	val id: String,
	val authorPubkey: String,
	val caption: String,
	val imageUrl: String?,
	val createdAt: Long
)

