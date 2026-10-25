package moe.fuqiuluo.xposed

/** System/vendor peers receive read-only snapshots rather than unrestricted command proxies. */
abstract class BaseDivineService {
    protected fun initDivineService(from: String, retryCount: Int = 0): Boolean = PeerBridge.start()
}
