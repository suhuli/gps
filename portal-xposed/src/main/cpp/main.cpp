#include <jni.h>
#include "sensor_hook.h"
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) { return JNI_VERSION_1_6; }
extern "C" JNIEXPORT jboolean JNICALL Java_moe_fuqiuluo_dobby_Dobby_install(JNIEnv*,jobject) { return installSensorHook(); }
extern "C" JNIEXPORT void JNICALL Java_moe_fuqiuluo_dobby_Dobby_setStatus(JNIEnv*,jobject,jboolean value) { setSensorEnabled(value); }
extern "C" JNIEXPORT jint JNICALL Java_moe_fuqiuluo_dobby_Dobby_status(JNIEnv*,jobject) { return sensorHookStatus(); }
extern "C" JNIEXPORT jlong JNICALL Java_moe_fuqiuluo_dobby_Dobby_rewritten(JNIEnv*,jobject) { return static_cast<jlong>(sensorRewriteCount()); }

extern "C" JNIEXPORT void JNICALL Java_moe_fuqiuluo_dobby_Dobby_heartbeat(JNIEnv*,jobject) { sensorHeartbeat(); }
