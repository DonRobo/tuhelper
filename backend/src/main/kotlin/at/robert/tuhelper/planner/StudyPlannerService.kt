package at.robert.tuhelper.planner

import at.robert.tuhelper.tugraz.StudyService
import org.springframework.stereotype.Service


@Service
class StudyPlannerService(
    private val studyService: StudyService,
) {

    fun planStudy(studyNumber: String): StudyPlan {
        val studyData = studyService.getStudyData(studyNumber)
        val studyPlanner = StudyPlanner(studyData)

        when (studyNumber) {
            "921" -> {
                studyPlanner.configure {
                    segment("Major") {
                        moduleGroups('A' to 'J') {
                            module("Compulsory 1") {
                                required()
                            }
                            module("Compulsory 2") {
                                required()
                            }
                            module("N") {
                                maxEcts = 4
                            }
                        }
                    }
                    segment("Minor") {
                        moduleGroups('A' to 'M') {
                            module("Compulsory 1") {
                                required()
                            }
                            excludeModule("N")
                        }
                    }
                    addSegment("Frei") {
                        addModuleGroup("Frei") {
                            addModule("Frei") {
                                addCourses(studyPlanner.allCourses())
                            }
                        }
                        requiredEcts = 6
                    }
                    addSegment("Masterarbeit") {
                        addModuleGroup("Masterarbeit") {
                            addModule("Masterarbeit") {
                                addCourse("Masterarbeit") {
                                    ects = 30
                                }
                            }
                        }
                        requiredEcts = 30
                    }
                }
            }

            else -> TODO("Study $studyNumber not supported yet")
        }

        return studyPlanner.solve()
    }

}
