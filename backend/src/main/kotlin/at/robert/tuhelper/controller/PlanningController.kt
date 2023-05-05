package at.robert.tuhelper.controller

import at.robert.tuhelper.planner.StudyPlan
import at.robert.tuhelper.planner.StudyPlannerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/planning")
class PlanningController(
    private val studyPlannerService: StudyPlannerService,
) {

    @GetMapping()
    fun demoPlan(): StudyPlan {
        return studyPlannerService.planStudy("921")
    }
}
