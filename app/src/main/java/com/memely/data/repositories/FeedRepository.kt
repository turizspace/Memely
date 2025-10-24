package com.memely.data.repositories

import com.memely.data.models.Meme

class FeedRepository {
	private val cache = mutableListOf<Meme>()

	suspend fun fetchRecent(): List<Meme> {
		// TODO: fetch from relays
		return cache.toList()
	}

	fun add(m: Meme) { cache.add(0, m) }
}

