package com.memely.data.repositories

import com.memely.data.models.UserProfile

class UserRepository {
	private val profiles = mutableMapOf<String, UserProfile>()

	fun put(profile: UserProfile) { profiles[profile.pubkey] = profile }

	fun get(pubkey: String): UserProfile? = profiles[pubkey]
}

