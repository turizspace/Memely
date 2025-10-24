package com.memely.meme

import java.io.File

object TemplateRepository {
    private val templates = mutableListOf<File>()

    fun listTemplates(): List<File> = templates.toList()

    fun addTemplate(f: File) { templates.add(f) }
}
