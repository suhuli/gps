import moe.microbios.nmea.NMEA
import moe.microbios.nmea.NmeaRewriter
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NmeaRewriterTest {
    private fun wire(body: String) = "$"+body+"*"+NMEA.calculateNmeaChecksum(body)+"\r\n"
    private fun rewrite(body: String, lat: Double = -33.5, lon: Double = -151.25): String? = NmeaRewriter.rewrite(wire(body),lat,lon,80.0,10.0,90.0,12)
    @Test fun southernAndWesternCoordinates() {
        val result = rewrite("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,")!!
        assertTrue(result.contains(",3330.000000,S,15115.000000,W,")); assertTrue(result.contains(",12,0.9,80.0,M,"))
    }
    @Test fun checksumIsRebuilt() { val result=rewrite("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,")!!; assertEquals(NMEA.calculateNmeaChecksum(result),result.substringAfter('*').trim()) }
    @Test fun speedConvertedToKnots() { val result=rewrite("GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W")!!; assertTrue(result.contains(",19.438,90.000,")) }
    @Test fun retainsTalker() { assertTrue(rewrite("GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W")!!.startsWith("\$GNRMC,")) }
    @Test fun invalidChecksumRejected() { assertNull(NmeaRewriter.rewrite("\$GPGGA,1*00",0.0,0.0,0.0,0.0,0.0,12)) }
    @Test fun invalidFixNotPromoted() { assertNull(rewrite("GPRMC,123519,V,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W")) }
    @Test fun unsupportedSentenceUntouched() { assertNull(rewrite("GPXXX,1,2,3")) }
    @Test fun localeIndependent() {
        val before=Locale.getDefault(); try { Locale.setDefault(Locale.GERMANY); assertTrue(rewrite("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,")!!.contains("3330.000000")) } finally { Locale.setDefault(before) }
    }
    @Test fun degreeRoundingDoesNotProduceSixtyMinutes() { val result=rewrite("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",12.9999999999,0.0)!!; assertTrue(result.contains(",1300.000000,N,")) }
}
