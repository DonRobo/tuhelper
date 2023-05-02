package at.robert.tuhelper.data

import java.math.BigDecimal

enum class StudyType {
    BACHELOR,
    INDIVIDUAL_BACHELOR,
    INDIVIDUAL_MASTER,
    MASTER,
    DOCTORATE,
    UNIVERSITY_COURSE,
    OTHER,
    TEACHING_EXTENSION,
    TEACHING_BACHELOR,
    TEACHING_MASTER,
}

data class Study(
    val number: String,
    val name: String,
    val ects: BigDecimal?,
    val type: StudyType
)
