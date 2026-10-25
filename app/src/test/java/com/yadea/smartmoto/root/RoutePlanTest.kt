package com.yadea.smartmoto.root

import org.junit.Assert.*
import org.junit.Test

class RoutePlanTest {
    private fun sample() = RoutePlan(name="测试路线", points=listOf(RoutePoint(31.2,121.4),RoutePoint(31.3,121.5)))
    @Test fun roundTrip() { val plan=sample(); assertEquals(plan,RouteCodec.decode(RouteCodec.encode(plan))) }
    @Test fun reverseIsIndependent() { val plan=sample(); val reversed=plan.reversed(); assertNotEquals(plan.id,reversed.id); assertEquals(plan.points.reversed(),reversed.points) }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidCoordinates() { sample().copy(points=listOf(RoutePoint(Double.NaN,1.0),RoutePoint(0.0,0.0))).validate() }
    @Test(expected=IllegalArgumentException::class) fun rejectsSinglePoint() { sample().copy(points=listOf(RoutePoint(0.0,0.0))).validate() }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnknownCoordinates() { sample().copy(coordinateType="unknown").validate() }
    @Test(expected=IllegalArgumentException::class) fun rejectsPathIdentifier() { sample().copy(id="../../route").validate() }
    @Test(expected=IllegalArgumentException::class) fun rejectsMissingFields() { RouteCodec.decode("{}") }
    @Test(expected=IllegalArgumentException::class) fun rejectsOversizedImport() { RouteCodec.decode(" ".repeat(RouteCodec.MAX_BYTES+1)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsAllDuplicatePoints() { sample().copy(points=listOf(RoutePoint(0.0,0.0),RoutePoint(0.0,0.0))).validate() }
    @Test fun portalHistoryImportedWithExplicitConsent() {
        val json="""[{"name":"Portal路线","route":[{"first":31.2,"second":121.4},{"first":31.3,"second":121.5}]}]"""
        val routes=RouteCodec.decodeMany(json,true); assertEquals(1,routes.size); assertEquals("gcj02",routes[0].coordinateType); assertEquals(31.2,routes[0].points.first().latitude,0.0)
    }
    @Test fun portalPairArraysImported() { assertEquals(2,RouteCodec.decodeMany("""{"name":"路线","route":[[31.2,121.4],[31.3,121.5]]}""",true)[0].points.size) }
    @Test(expected=IllegalArgumentException::class) fun unknownLegacyCoordinatesRequireConsent() { RouteCodec.decodeMany("""{"name":"路线","route":[[31.2,121.4],[31.3,121.5]]}""") }
    @Test(expected=IllegalArgumentException::class) fun deepJsonRejected() { RouteCodec.decodeMany("[".repeat(40)+"]".repeat(40)) }
    @Test fun ownRouteListImported() { val plan=sample(); assertEquals(plan,RouteCodec.decodeMany("["+RouteCodec.encode(plan)+"]")[0]) }
    @Test fun northwardStep() { val point=MotionMath.step(RoutePoint(0.0,0.0),0.0,1000.0); assertEquals(0.0089932,point.latitude,0.000001); assertEquals(0.0,point.longitude,0.000001) }
    @Test fun wrapsAntimeridian() { val point=MotionMath.step(RoutePoint(0.0,179.999),90.0,1000.0); assertTrue(point.longitude in -180.0..-179.0) }
    @Test fun zeroDistanceDoesNotMove() { val point=RoutePoint(30.0,120.0); val result=MotionMath.step(point,25.0,0.0); assertEquals(point.latitude,result.latitude,1e-10); assertEquals(point.longitude,result.longitude,1e-10) }
    @Test fun motionStops() { RootMotion.set(-90.0,10.0,true); assertEquals(270.0,RootMotion.command.bearing,0.0); RootMotion.stop(); assertEquals(0.0,RootMotion.command.speedKmh,0.0); assertFalse(RootMotion.command.automatic) }
}
