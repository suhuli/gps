package moe.fuqiuluo.xposed

object HookCapabilities {
    private val entries = java.util.concurrent.ConcurrentHashMap<String,String>()
    fun record(name: String, state: String) { entries[name]=state.take(200) }
    fun report(): String = entries.toSortedMap().entries.joinToString("\n") { "${it.key}: ${it.value}" }
}
