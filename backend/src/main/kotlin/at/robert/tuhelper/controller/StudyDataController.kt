package at.robert.tuhelper.controller

import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyData
import at.robert.tuhelper.tugraz.EffortService
import at.robert.tuhelper.tugraz.StudyService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("data")
class StudyDataController(
    private val studyService: StudyService,
    private val effortService: EffortService,
) {

    @GetMapping("studies")
    fun studies(): List<Study> {
        return studyService.getStudies()
    }

    @GetMapping("studies/{studyNumber}")
    fun study(@PathVariable studyNumber: String): StudyData {
        return studyService.getStudyData(studyNumber)
    }

    @GetMapping("efforts")
    fun courseEfforts(): Map<String, Double> {
        return effortService.getAllEffortMultipliers()
    }

    @GetMapping("effort")
    fun courseEffort(@RequestParam courseName: String): Double {
        return effortService.getEffort(courseName)
    }

    @PutMapping("effort")
    fun setEffort(@RequestParam courseName: String, @RequestParam effort: Double) {
        effortService.setEffort(courseName, effort)
    }

}
