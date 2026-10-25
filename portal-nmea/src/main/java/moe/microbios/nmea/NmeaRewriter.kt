package moe.microbios.nmea

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Rewrites supported fix sentences, retaining talker IDs and rebuilding the wire checksum. */
object NmeaRewriter {
    fun rewrite(sentence: String, latitude: Double, longitude: Double, altitude: Double,
                speedMps: Double, bearing: Double, satellites: Int): String? {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        require(altitude.isFinite() && speedMps.isFinite() && speedMps >= 0 && bearing.isFinite() && satellites in 0..35)
        if (sentence.length > 1024) return null
        val line = sentence.trimEnd('\r','\n')
        if (!line.startsWith('$')) return null
        val body = line.drop(1).substringBefore('*')
        if (body.any { it.code !in 32..126 }) return null
        val checksum = body.fold(0) { total, ch -> total xor ch.code }
        if ('*' in line && line.substringAfter('*').uppercase(Locale.US) != "%02X".format(Locale.US,checksum)) return null
        val fields = body.split(',').toMutableList()
        if (fields[0].length != 5) return null
        fun number(value: Double, format: String) = String.format(Locale.US,format,value)
        fun degrees(value: Double, longitude: Boolean): String {
            val absolute = abs(value); var degree = floor(absolute).toInt()
            var minute = kotlin.math.round((absolute-degree)*60*1000000)/1000000
            if (minute >= 60) { degree++; minute=0.0 }
            return String.format(Locale.US,if(longitude) "%03d%09.6f" else "%02d%09.6f",degree,minute)
        }
        fun point(index: Int) {
            fields[index]=degrees(latitude,false); fields[index+1]=if(latitude>=0) "N" else "S"
            fields[index+2]=degrees(longitude,true); fields[index+3]=if(longitude>=0) "E" else "W"
        }
        when(fields[0].takeLast(3)) {
            "GGA" -> { if(fields.size<15 || (fields[6].toIntOrNull() ?: 0)==0) return null; point(2); fields[7]=satellites.toString().padStart(2,'0'); fields[9]=number(altitude,"%.1f"); fields[10]="M" }
            "GNS" -> { if(fields.size<13 || fields[6].all { it=='N' || it==' ' }) return null; point(2); fields[7]=satellites.toString().padStart(2,'0'); fields[9]=number(altitude,"%.1f") }
            "RMC" -> { if(fields.size<12 || fields[2]!="A") return null; point(3); fields[7]=number(speedMps*1.9438444924,"%.3f"); fields[8]=number(((bearing%360)+360)%360,"%.3f") }
            "GLL" -> { if(fields.size<7 || fields[6]!="A") return null; point(1) }
            "VTG" -> { if(fields.size<9) return null; fields[1]=number(((bearing%360)+360)%360,"%.3f"); fields[5]=number(speedMps*1.9438444924,"%.3f"); fields[7]=number(speedMps*3.6,"%.3f") }
            else -> return null
        }
        val rewritten = fields.joinToString(",")
        val rebuilt = rewritten.fold(0) { total, ch -> total xor ch.code }
        return "$"+rewritten+"*"+String.format(Locale.US,"%02X",rebuilt)+"\r\n"
    }
}
