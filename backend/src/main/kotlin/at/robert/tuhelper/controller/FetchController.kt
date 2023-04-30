package at.robert.tuhelper.controller

import at.robert.tuhelper.data.Study
import at.robert.tuhelper.tugraz.StudyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(
    private val studyService: StudyService,
) {

    @GetMapping("studies")
    fun studies(): List<Study> {
        return studyService.getStudies()
    }

}
