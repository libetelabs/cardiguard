package ai.cardiguard.app.domain

/**
 * 모든 안내 문구의 출처. 주행 중 문구는 세 문장, 45자 이하로 유지한다(PlaybookTest가 검사).
 * 일반 안내이며 법률 자문이나 과실 판단이 아니다.
 */
object Playbook {
    /**
     * 단계마다 붙는 그림. 차 안에서는 글을 읽기 전에 그림이 먼저 눈에 든다.
     * 도메인은 그릴 방법을 모른다. 무엇을 뜻하는지만 정하고 그리는 일은 화면이 맡는다.
     */
    enum class StepIcon {
        MEDICAL, HAZARD, ESCAPE, PHOTO, INSURANCE, POLICE, FAULT,
        VIDEO, ARCHIVE, REPORT, LOCK, NO_ARGUE, TOW,
    }

    data class Guide(
        val title: String,
        val drivingLine: String,
        val parkedSteps: List<String>,
        val stepIcons: List<StepIcon> = emptyList(),
    )

    const val DISCLAIMER = "일반 안내이며 법률 자문이나 과실 판단이 아닙니다."

    const val ASK_COLLISION = "부딪히셨나요?"
    const val ASK_THREAT = "급제동이 반복됩니다. 위협받고 계신가요?"
    const val ASK_CALL = "112에 연결할까요?"
    const val DOOR_WARNING = "지금은 내리지 마세요."
    const val NO_CALL = "필요하면 112라고 말씀하세요."
    const val DISMISSED = "안내를 마칩니다."
    const val PARKED_READY = "화면에 다음 할 일을 띄웠습니다."
    const val WARM_UP = "카디가드 준비됐습니다."
    const val NOT_UNDERSTOOD = "다시 한 번 말씀해 주세요."

    fun guide(type: IncidentType): Guide = when (type) {
        IncidentType.MANUAL_RECORD -> Guide(
            title = "기록한 순간",
            drivingLine = "시각과 위치를 저장했습니다.",
            parkedSteps = listOf(
                "블랙박스 영상이 덮어쓰이기 전에 이 시각의 파일을 따로 보관하세요.",
                "필요하면 영상과 함께 제보 여부를 결정하세요.",
            ),
            stepIcons = listOf(StepIcon.ARCHIVE, StepIcon.REPORT),
        )
        IncidentType.SUDDEN_BRAKE -> Guide(
            title = "급제동",
            drivingLine = "시각과 위치를 기록했습니다. 따라가지 마세요.",
            parkedSteps = listOf(
                "급제동 기록만으로는 과실을 알 수 없습니다. 영상으로 확인하세요.",
                "블랙박스 영상이 덮어쓰이기 전에 이 시각의 파일을 따로 보관하세요.",
                "위험 운전을 제보하려면 영상을 확인한 뒤 결정하세요.",
            ),
            stepIcons = listOf(StepIcon.VIDEO, StepIcon.ARCHIVE, StepIcon.REPORT),
        )
        IncidentType.THREAT -> Guide(
            title = "위협 상황",
            drivingLine = "문을 잠급니다. 내리지 말고 사람 많은 곳으로 이동하세요.",
            parkedSteps = listOf(
                "문을 잠근 채 차 안에 머무르세요.",
                "위험하다고 느끼면 바로 112에 신고하세요.",
                "상대와 말다툼하거나 맞대응하지 마세요.",
                "블랙박스 영상을 따로 보관하세요.",
            ),
            stepIcons = listOf(StepIcon.LOCK, StepIcon.POLICE, StepIcon.NO_ARGUE, StepIcon.ARCHIVE),
        )
        IncidentType.COLLISION -> Guide(
            title = "접촉 사고",
            drivingLine = "비상등을 켜고 안전한 곳에 세우세요. 다친 사람이 있으면 119가 먼저예요.",
            parkedSteps = listOf(
                "다친 사람을 확인하고, 있으면 119에 연락하세요.",
                "비상등을 켜고 뒤쪽 차량이 볼 수 있게 조치하세요.",
                "고속도로라면 차에 머물지 말고 가드레일 밖으로 대피하세요.",
                "차량 전체, 파손 부위, 번호판, 도로 상황을 사진으로 남기세요.",
                "상대방 연락처와 보험사를 확인하고 내 보험사에 접수하세요.",
                "다친 사람이 있거나 상대가 떠나면 112에 신고하세요.",
                "과실은 현장에서 단정하지 말고 보험사 판단에 맡기세요.",
            ),
            stepIcons = listOf(
                StepIcon.MEDICAL, StepIcon.HAZARD, StepIcon.ESCAPE, StepIcon.PHOTO,
                StepIcon.INSURANCE, StepIcon.POLICE, StepIcon.FAULT,
            ),
        )
        IncidentType.BREAKDOWN -> Guide(
            title = "고장 정차",
            drivingLine = "비상등을 켜세요. 고속도로라면 가드레일 밖으로 대피하세요.",
            parkedSteps = listOf(
                "비상등을 켜고 트렁크를 열어 뒤 차량에 알리세요.",
                "고속도로라면 차에 머물지 말고 가드레일 밖으로 대피하세요.",
                "안전한 곳에서 112나 보험사 긴급출동에 연락하세요.",
            ),
            stepIcons = listOf(StepIcon.HAZARD, StepIcon.ESCAPE, StepIcon.TOW),
        )
    }

    /** 경찰·보험사에 그대로 읽을 수 있는 접수 문장. */
    fun reportText(record: IncidentRecord, timeLabel: String, placeLabel: String?): String {
        val place = placeLabel ?: "위치 미확인"
        return "$timeLabel, $place · ${guide(record.type).title} 기록"
    }
}
