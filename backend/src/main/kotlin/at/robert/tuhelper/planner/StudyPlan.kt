package at.robert.tuhelper.planner


data class StudyPlan(
    val studySegments: List<PlannedStudySegment>
)

data class PlannedStudySegment(
    val id: Int,
    val name: String,
    val requiredEcts: Float,
    val moduleGroups: List<PlannedModuleGroup>,
)

data class PlannedModuleGroup(
    val id: Int,
    val name: String,
    val modules: List<PlannedStudyModule>,
)

data class PlannedStudyModule(
    val id: Int,
    val name: String,
    val courses: List<PlannedStudyCourse>,
    val required: Boolean,
)

data class PlannedStudyCourse(
    val id: Int,
    val name: String,
    val ects: Float,
    val effort: Float,
    val required: Boolean
)
