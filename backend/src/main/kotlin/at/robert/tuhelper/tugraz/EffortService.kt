package at.robert.tuhelper.tugraz

import at.robert.tuhelper.data.StudyCourse
import at.robert.tuhelper.repository.EffortRepository
import org.springframework.stereotype.Service

@Service
class EffortService(
    private val effortRepository: EffortRepository,
) {
    fun getAllEffortMultipliers(): Map<String, Double> {
        return effortRepository.getAllEffortMultipliers()
    }

    fun setEffort(courseName: String, effort: Double) {
        effortRepository.upsertEffort(courseName.actualCourseName, effort)
    }

    fun getEffort(courseName: String): Double {
        return effortRepository.getEffort(courseName.actualCourseName) ?: 1.0
    }

    private val String.actualCourseName: String
        get() = StudyCourse(
            -1,
            this,
            null
        ).actualName
}
