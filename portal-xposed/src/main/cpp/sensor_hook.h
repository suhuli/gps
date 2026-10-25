#pragma once
#include <cstdint>
bool installSensorHook();
void setSensorEnabled(bool value);
int sensorHookStatus();
uint64_t sensorRewriteCount();

void sensorHeartbeat();
