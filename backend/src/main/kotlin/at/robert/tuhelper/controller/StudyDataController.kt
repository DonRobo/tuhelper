package at.robert.tuhelper.controller

import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyData
import at.robert.tuhelper.tugraz.StudyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class StudyDataController(
    private val studyService: StudyService,
) {

    @GetMapping("studies")
    fun studies(): List<Study> {
        return studyService.getStudies()
    }

    @GetMapping("studies/{studyNumber}")
    fun study(@PathVariable studyNumber: String): StudyData {
        return studyService.getStudyData(studyNumber)
    }

}
