package at.robert.tuhelper.planner

import at.robert.tuhelper.repository.EffortRepository
import at.robert.tuhelper.tugraz.StudyService
import org.springframework.stereotype.Service


@Service
class StudyPlannerService(
    private val studyService: StudyService,
    private val effortRepository: EffortRepository,
) {

    fun planStudy(studyNumber: String): StudyPlan {
        val studyData = studyService.getStudyData(studyNumber)
        val effortData = effortRepository.getAllEffortMultipliers()
        val studyPlanner = StudyPlanner(studyData, effortData)

        when (studyNumber) {
            "921" -> {
                studyPlanner.configure {
                    segment("Major") {
                        moduleGroups('A' to 'J') {
                            module("Compulsory 1") {
                                required = true
                            }
                            module("Compulsory 2") {
                                required = true
                            }
                            module("N") {
                                maxEcts = 4f
                            }
                            handleDefaults = DefaultHandling.ADD
                        }
                        handleDefaults = DefaultHandling.IGNORE
                    }
                    handleDefaults = DefaultHandling.IGNORE
                    segment("Minor") {
                        moduleGroups('A' to 'M') {
                            module("Compulsory 1") {
                                required = true
                            }
                            excludeModule("N")
                            handleDefaults = DefaultHandling.ADD
                        }
                        handleDefaults = DefaultHandling.IGNORE
                    }
                    addSegment("Frei") {
                        addModuleGroup("Frei") {
                            addModule("Frei") {
                                addCourses(studyPlanner.allCourses()) {
                                }
                            }
                        }
                        requiredEcts = 6f
                    }
                    addSegment("Masterarbeit") {
                        addModuleGroup("Masterarbeit") {
                            addModule("Masterarbeit") {
                                addCourse("Masterarbeit") {
                                    ects = 30f
                                    required = true
                                }
                            }
                        }
                        requiredEcts = 30f
                    }
                }
            }

            else -> TODO("Study $studyNumber not supported yet")
        }

        return studyPlanner.solve()
    }

}
