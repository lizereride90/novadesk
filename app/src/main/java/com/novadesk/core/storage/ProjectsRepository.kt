package com.novadesk.core.storage

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Project list persisted as one tiny line-based file
 * (`id|name|rootPath|preferredAgent|lastOpenedAt`). No database needed:
 * users have dozens of projects at most, and a flat file is trivially
 * debuggable with `cat`.
 */
class ProjectsRepository(private val dir: File) {

    private val store = File(dir, "projects.list")
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!store.exists()) {
            _projects.value = emptyList()
            return@withContext
        }
        _projects.value = store.readLines()
            .mapNotNull { it.split("|").takeIf { p -> p.size >= 5 } }
            .map {
                Project(
                    id = it[0], name = it[1].unescape(), rootPath = it[2].unescape(),
                    preferredAgent = it[3], lastOpenedAt = it[4].toLongOrNull() ?: 0L,
                )
            }
            .sortedByDescending { p -> p.lastOpenedAt }
    }

    suspend fun upsert(project: Project) = withContext(Dispatchers.IO) {
        val next = (_projects.value.filterNot { it.id == project.id } + project)
            .sortedByDescending { it.lastOpenedAt }
        persist(next)
        _projects.value = next
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val next = _projects.value.filterNot { it.id == id }
        persist(next)
        _projects.value = next
    }

    private fun persist(list: List<Project>) {
        store.parentFile?.mkdirs()
        store.writeText(
            list.joinToString("\n") {
                "${it.id}|${it.name.escape()}|${it.rootPath.escape()}|${it.preferredAgent}|${it.lastOpenedAt}"
            },
        )
    }

    private fun String.escape() = replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
    private fun String.unescape() = replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\")
}
