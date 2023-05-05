package at.robert.tuhelper.planner

import at.robert.tuhelper.data.*
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.BoolVar

class StudyPlanner(
    private val studyData: StudyData
) {
    private var config = StudyPlannerConfig()

    fun configure(block: StudyPlannerConfig.() -> Unit) {
        val config = StudyPlannerConfig()
        config.block()
        this.config = config
    }

    fun solve(): StudyPlan {
        val model = Model("Master")

        val configurationStudyPlan = generateForModel(model)

        model.solver.solve()

        return configurationStudyPlan.generate()
    }

    private fun generateForModel(model: Model): ConfigurationStudyPlan {
        return ConfigurationStudyPlan(
            config.segmentsConfigs.map { (selector, conf) ->
                val segment = selector.chooseSegment(studyData.segments)
                ConfigurationSegment(
                    segment,
                    conf.moduleGroups.map { (selector, conf) ->
                        val moduleGroup = selector.chooseModuleGroup(segment.moduleGroups)
                        ConfigurationModuleGroup(
                            moduleGroup,
                            conf.modules.mapNotNull { (selector, conf) ->
                                ConfigurationModule(
                                    selector.chooseModule(moduleGroup.modules) ?: return@mapNotNull null,
                                    emptyList()
                                )
                            },
                            model.boolVar()
                        )
                    }
                )
            }
        )
    }

    private fun ConfigurationStudyPlan.generate(): StudyPlan {
        return StudyPlan(
            this.configurationSegments.map {
                it.generate()
            }
        )
    }

    private fun ConfigurationSegment.generate(): StudySegment {
        return studySegment.copy(
            moduleGroups = moduleGroups.mapNotNull {
                it.generate()
            }
        )
    }

    private fun ConfigurationModuleGroup.generate(): StudyModuleGroup? {
        return if (chosen.value == 1) {
            studyModuleGroup.copy(
                modules = modules.mapNotNull {
                    it.generate()
                }
            )
        } else {
            null
        }
    }

    private fun ConfigurationModule.generate(): StudyModule? {
        return studyModule.copy(
            courses = courses.mapNotNull {
                it.generate()
            }
        ).let {
            if (it.courses.isEmpty()) null else it
        }
    }

    private fun ConfigurationCourse.generate(): Course? {
        return if (chosen.value == 1) course else null
    }

    fun allModuleGroups(): List<StudyModuleGroup> {
        return studyData.segments.flatMap { segment ->
            segment.moduleGroups
        }.distinctBy { it.id }
    }

    fun allCourses(): List<Course> {
        return studyData.segments.flatMap { segment ->
            segment.moduleGroups.flatMap { moduleGroup ->
                moduleGroup.modules.flatMap { module ->
                    module.courses
                }
            }
        }.distinctBy { it.id }
    }

    data class ConfigurationStudyPlan(
        val configurationSegments: List<ConfigurationSegment>
    )

    data class ConfigurationSegment(
        val studySegment: StudySegment,
        val moduleGroups: List<ConfigurationModuleGroup>
    )

    data class ConfigurationModuleGroup(
        val studyModuleGroup: StudyModuleGroup,
        val modules: List<ConfigurationModule>,
        val chosen: BoolVar,
    )

    data class ConfigurationModule(
        val studyModule: StudyModule,
        val courses: List<ConfigurationCourse>
    )

    data class ConfigurationCourse(
        val course: Course,
        val chosen: BoolVar
    )
}
