// Portal-derived native integration, rewritten to use the public NDK event layout.
// No fixed-offset writes, negative counters, radio changes, or external library paths.
#include <android/sensor.h>
#include <dobby.h>
#include <atomic>
#include <array>
#include <algorithm>
#include <cstdint>
#include <limits>
#include <sys/types.h>
#include <time.h>
#include "sensor_hook.h"

namespace {
using Write = ssize_t (*)(const void*, const ASensorEvent*, size_t);
std::atomic<Write> original{nullptr};
std::atomic<bool> enabled{false};
std::atomic<bool> installed{false};
std::atomic<uint64_t> rewritten{0};
std::atomic<int64_t> deadline{0};
int64_t awakeMillis() { timespec time{}; clock_gettime(CLOCK_MONOTONIC,&time); return static_cast<int64_t>(time.tv_sec)*1000+time.tv_nsec/1000000; }
std::atomic<uint64_t> baseline{std::numeric_limits<uint64_t>::max()};
ssize_t writeEvents(const void* tube, const ASensorEvent* events, size_t count) {
    const auto call=original.load(std::memory_order_acquire);
    if (!call) return 0; // Only the install-time trampoline publication window.
    if (!enabled.load(std::memory_order_acquire) || awakeMillis() > deadline.load() || !events || count > 256) return call(tube,events,count);
    std::array<ASensorEvent,256> copy;
    size_t output=0; uint64_t changed=0;
    for(size_t i=0;i<count;++i) {
        // The producer's public ABI must match this build. Otherwise leave the entire batch alone.
        if(events[i].version != sizeof(ASensorEvent)) return call(tube,events,count);
        ASensorEvent event=events[i];
        switch(event.type) {
            case 18: ++changed; continue; // Step detector: no synthetic step notifications.
            case 19: { // Step counter: hold a nonnegative monotonic baseline, never write -1.
                uint64_t unset=std::numeric_limits<uint64_t>::max();
                baseline.compare_exchange_strong(unset,event.u64.step_counter);
                event.u64.step_counter=baseline.load(); ++changed; break;
            }
            case 1: case 9: // Accelerometer/gravity: stationary, upright handset.
                std::fill(std::begin(event.data),std::end(event.data),0.0f); event.data[2]=9.80665f; ++changed; break;
            case 4: case 10: // Gyroscope / linear acceleration.
                std::fill(std::begin(event.data),std::end(event.data),0.0f); ++changed; break;
            case 11: case 15: case 20: // Rotation vectors: identity quaternion.
                std::fill(std::begin(event.data),std::end(event.data),0.0f); event.data[3]=1.0f; ++changed; break;
            default: break; // Light, proximity, temperature, metadata etc. remain untouched.
        }
        copy[output++]=event;
    }
    rewritten.fetch_add(changed,std::memory_order_relaxed);
    if(output == 0) return static_cast<ssize_t>(count);
    const ssize_t written=call(tube,copy.data(),output);
    return written == static_cast<ssize_t>(output) ? static_cast<ssize_t>(count) : written;
}
}

bool installSensorHook() {
    if(installed.load()) return true;
    const char* symbol = sizeof(void*) == 8
        ? "_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventm"
        : "_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventj";
    void* target=nullptr;
    for(const char* library : {"libsensor.so", "libsensorservice.so", "libgui.so"}) {
        target=DobbySymbolResolver(library,symbol); if(target) break;
    }
    if(!target) return false;
    void* trampoline=nullptr;
    if(DobbyHook(target,reinterpret_cast<void*>(writeEvents),&trampoline) != 0 || !trampoline) return false;
    original.store(reinterpret_cast<Write>(trampoline),std::memory_order_release);
    installed.store(true); return true;
}
void setSensorEnabled(bool value) {
    if(!value) enabled.store(false,std::memory_order_release);
    baseline.store(std::numeric_limits<uint64_t>::max());
    if(value && installed.load()) { deadline.store(awakeMillis()+15000); enabled.store(true,std::memory_order_release); }
}
int sensorHookStatus() { return (installed.load()?1:0) | (enabled.load() && awakeMillis()<=deadline.load()?2:0); }
uint64_t sensorRewriteCount() { return rewritten.load(); }

void sensorHeartbeat() { deadline.store(awakeMillis()+15000); }
