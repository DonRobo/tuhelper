package at.robert.tuhelper.data

import java.math.BigDecimal

data class StudyData(
    val study: Study,
    val segments: List<StudySegment>
)

data class StudySegment(
    val id: Int,
    val name: String,
    val ects: BigDecimal?,
    val moduleGroups: List<StudyModuleGroup>
)

data class StudyModuleGroup(
    val id: Int,
    val name: String,
    val modules: List<StudyModule>
)

data class StudyModule(
    val id: Int,
    val name: String,
    val courses: List<Course>
)

data class Course(
    val id: Int,
    val name: String,
    val ects: BigDecimal?,
)
