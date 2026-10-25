package com.yadea.smartmoto.root

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** App-private, atomic storage. Imported identifiers never become arbitrary filesystem paths. */
class RouteRepository(context: Context) {
    private val directory = File(context.filesDir, "root_routes").apply { check(exists() || mkdirs()) }
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id.lowercase())
        return File(directory, "$id.json")
    }
    @Synchronized fun list(): List<RoutePlan> = directory.listFiles().orEmpty()
        .map { if (it.name.endsWith(".json.bak")) File(directory,it.name.removeSuffix(".bak")) else it }
        .filter { it.name.endsWith(".json") }.distinctBy { it.name }.mapNotNull {
            runCatching { val atomic = AtomicFile(it); atomic.openRead().use { input ->
                val bytes = input.readBytesLimited(); RouteCodec.decode(bytes.toString(Charsets.UTF_8))
            } }.getOrNull()
        }.sortedBy { it.name }
    @Synchronized fun save(plan: RoutePlan) {
        val bytes = RouteCodec.encode(plan).toByteArray(Charsets.UTF_8)
        require(bytes.size <= RouteCodec.MAX_BYTES)
        val atomic = AtomicFile(file(plan.id)); val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }
    @Synchronized fun delete(plan: RoutePlan) { AtomicFile(file(plan.id)).delete() }
}
fun java.io.InputStream.readBytesLimited(): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) break
        require(output.size() + count <= RouteCodec.MAX_BYTES) { "文件超过 5 MB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
