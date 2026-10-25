package moe.fuqiuluo.dobby

object Dobby {
    external fun install(): Boolean
    external fun setStatus(status: Boolean)
    external fun heartbeat()
    external fun status(): Int
    external fun rewritten(): Long
}
