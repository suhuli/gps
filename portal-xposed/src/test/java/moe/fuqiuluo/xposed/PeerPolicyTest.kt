package moe.fuqiuluo.xposed
import org.junit.Assert.*
import org.junit.Test
class PeerPolicyTest {
    @Test fun systemPhoneAdmitted() { assertTrue(PeerPolicy.allowed("com.android.phone",true)) }
    @Test fun userInstalledImpersonatorRejected() { assertFalse(PeerPolicy.allowed("com.android.phone",false)) }
    @Test fun unrelatedSystemAppRejected() { assertFalse(PeerPolicy.allowed("com.other.app",true)) }
    @Test fun finiteFrameAccepted() { assertTrue(PeerPolicy.valid(30.0,120.0,10.0,80.0,1f,270.0)) }
    @Test fun invalidFrameRejected() { assertFalse(PeerPolicy.valid(Double.NaN,120.0,10.0,80.0,1f,270.0)); assertFalse(PeerPolicy.valid(30.0,120.0,10.0,80.0,1f,Double.POSITIVE_INFINITY)) }
    @Test fun uninitializedLeaseExpired() { assertTrue(PeerPolicy.expired(0,200)) }
    @Test fun expiredLeaseDisables() { assertTrue(PeerPolicy.expired(1000,16001)); assertFalse(PeerPolicy.expired(1000,16000)) }
    @Test fun backwardsClockRejected() { assertTrue(PeerPolicy.expired(2000,1000)) }
    @Test fun flagsCannotEnableWrongProfile() { assertFalse(PeerPolicy.enabledFor("com.android.phone",true,false,true)); assertFalse(PeerPolicy.enabledFor("com.oplus.location",true,true,false)) }
    @Test fun inactiveSessionCannotEnablePeer() { assertFalse(PeerPolicy.enabledFor("com.android.phone",false,true,true)) }
}
