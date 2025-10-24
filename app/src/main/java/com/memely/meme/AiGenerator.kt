package com.memely.meme

import java.io.File

/**
 * AI image generation scaffold. Replace with real AI image generation API.
 */
object AiGenerator {
    suspend fun generateFromPrompt(prompt: String): File {
        // TODO: call AI service to generate image and return local file
        val f = File.createTempFile("ai_gen", "png")
        return f
    }
}
