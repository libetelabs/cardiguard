package ai.cardiguard.app.car

import ai.cardiguard.app.domain.Cause
import ai.cardiguard.app.domain.DriveSession
import ai.cardiguard.app.domain.IncidentLog
import ai.cardiguard.app.domain.IncidentRecord
import ai.cardiguard.app.domain.IncidentType
import ai.cardiguard.app.domain.Trigger
import ai.cardiguard.app.domain.VehicleSnapshot
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 기록은 이 기기에만 남는다. 외부로 보내지 않는다. */
class PrefsIncidentLog(context: Context) : IncidentLog {
    private val prefs = context.applicationContext.getSharedPreferences("incidents", Context.MODE_PRIVATE)

    /** 같은 id는 격상되거나 라벨이 붙은 기록이므로 덮어쓴다. */
    override fun save(record: IncidentRecord) {
        val kept = all().filterNot { it.id == record.id }
        write(RECORDS, (kept + record).map(::toJson))
    }

    override fun all(): List<IncidentRecord> = read(RECORDS).map(::recordFromJson)

    override fun save(session: DriveSession) {
        val kept = sessions().filterNot { it.id == session.id }
        write(SESSIONS, (kept + session).map(::toJson))
    }

    override fun sessions(): List<DriveSession> = read(SESSIONS).map(::sessionFromJson)

    private fun write(key: String, items: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(items).toString()).apply()
    }

    private fun read(key: String): List<JSONObject> {
        val json = JSONArray(prefs.getString(key, "[]"))
        return List(json.length()) { json.getJSONObject(it) }
    }

    private fun toJson(record: IncidentRecord) = JSONObject().apply {
        put("id", record.id)
        put("sessionId", record.sessionId ?: JSONObject.NULL)
        put("at", record.atMs)
        put("type", record.type.name)
        put("trigger", record.trigger.name)
        put("location", JSONObject().apply {
            put("lat", record.latitude ?: JSONObject.NULL)
            put("lng", record.longitude ?: JSONObject.NULL)
        })
        put("cause", record.cause?.name ?: JSONObject.NULL)
        put("labeledAt", record.labeledAtMs ?: JSONObject.NULL)
        put("vehicle", JSONObject().apply {
            put("speedBefore", record.vehicle.speedBefore?.toDouble() ?: JSONObject.NULL)
            put("speedAfter", record.vehicle.speedAfter?.toDouble() ?: JSONObject.NULL)
            put("gear", record.vehicle.gear ?: JSONObject.NULL)
            put("brakePressed", record.vehicle.brakePressed ?: JSONObject.NULL)
            put("airbag", record.vehicle.airbag)
            put("doorLocked", record.vehicle.doorLocked ?: JSONObject.NULL)
        })
    }

    private fun recordFromJson(json: JSONObject): IncidentRecord {
        val location = json.getJSONObject("location")
        val vehicle = json.getJSONObject("vehicle")
        return IncidentRecord(
            id = json.getLong("id"),
            sessionId = json.longOrNull("sessionId"),
            atMs = json.getLong("at"),
            type = IncidentType.valueOf(json.getString("type")),
            trigger = Trigger.valueOf(json.getString("trigger")),
            latitude = location.doubleOrNull("lat"),
            longitude = location.doubleOrNull("lng"),
            cause = json.stringOrNull("cause")?.let(Cause::valueOf),
            labeledAtMs = json.longOrNull("labeledAt"),
            vehicle = VehicleSnapshot(
                speedBefore = vehicle.doubleOrNull("speedBefore")?.toFloat(),
                speedAfter = vehicle.doubleOrNull("speedAfter")?.toFloat(),
                gear = vehicle.stringOrNull("gear"),
                brakePressed = vehicle.booleanOrNull("brakePressed"),
                airbag = vehicle.getBoolean("airbag"),
                doorLocked = vehicle.booleanOrNull("doorLocked"),
            ),
        )
    }

    private fun toJson(session: DriveSession) = JSONObject().apply {
        put("id", session.id)
        put("startedAt", session.startedAtMs)
        put("endedAt", session.endedAtMs ?: JSONObject.NULL)
        put("distanceKm", session.distanceKm)
        put("hardBrakes", session.hardBrakes)
        put("events", JSONArray(session.events))
    }

    private fun sessionFromJson(json: JSONObject): DriveSession {
        val events = json.getJSONArray("events")
        return DriveSession(
            id = json.getLong("id"),
            startedAtMs = json.getLong("startedAt"),
            endedAtMs = json.longOrNull("endedAt"),
            distanceKm = json.getDouble("distanceKm"),
            hardBrakes = json.getInt("hardBrakes"),
            events = List(events.length(), events::getLong),
        )
    }

    private fun JSONObject.doubleOrNull(key: String) = if (isNull(key)) null else getDouble(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.booleanOrNull(key: String) = if (isNull(key)) null else getBoolean(key)

    private companion object {
        const val RECORDS = "records_v2"
        const val SESSIONS = "sessions"
    }
}
