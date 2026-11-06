package ai.cardiguard.app.domain

/** 앱이 사건 기록을 어디에 두는지와 무관하게 쓰는 저장소 경계. */
interface IncidentLog {
    fun save(record: IncidentRecord)
    fun all(): List<IncidentRecord>
    fun save(session: DriveSession)
    fun sessions(): List<DriveSession>
}
