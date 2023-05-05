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

    private fun generateForModel(model: Model): SolverStudyPlan {
        fun configureCourse(name: String, conf: CourseConfig): SolverCourse {
            return SolverCourse(
                Course(
                    id = -1,
                    name = name,
                    ects = conf.ects?.toBigDecimal()
                ),
                model.boolVar()
            )
        }

        fun configureCourse(course: Course): SolverCourse {
            return SolverCourse(
                course,
                model.boolVar()
            )
        }

        fun configureModule(
            moduleOptions: List<StudyModule>,
            selector: ModuleSelector,
            conf: ModuleConfig
        ): SolverModule? {
            require(!conf.required || !conf.excluded)
            if (conf.excluded) return null

            val module = selector.chooseModule(moduleOptions) ?: return null
            return SolverModule(
                module,
                conf.courses.map { (name, courseConf) ->
                    configureCourse(name, courseConf)
                } + conf.coursesToAdd.map { course ->
                    configureCourse(course)
                },
                model.boolVar().also {
                    if (conf.required)
                        it.eq(1).post()
                }
            )
        }

        fun configureModuleGroup(
            moduleGroupOptions: List<StudyModuleGroup>,
            selector: ModuleGroupSelector,
            conf: ModuleGroupConfig
        ): SolverModuleGroup {
            val moduleGroup = selector.chooseModuleGroup(moduleGroupOptions)
            return SolverModuleGroup(
                moduleGroup,
                conf.modules.mapNotNull { (selector, conf) ->
                    configureModule(moduleGroup.modules, selector, conf)
                },
                model.boolVar()
            )
        }

        fun configureSegment(selector: SegmentSelector, conf: SegmentConfig): SolverSegment {
            val segment = selector.chooseSegment(studyData.segments)
            return SolverSegment(
                segment,
                conf.moduleGroups.map { (selector, conf) ->
                    configureModuleGroup(segment.moduleGroups, selector, conf)
                }
            )
        }

        return SolverStudyPlan(
            config.segmentsConfigs.map { (selector, conf) ->
                configureSegment(selector, conf)
            }
        )
    }

    private fun SolverStudyPlan.generate(): StudyPlan {
        return StudyPlan(
            this.solverSegments.map {
                it.generate()
            }
        )
    }

    private fun SolverSegment.generate(): StudySegment {
        return studySegment.copy(
            moduleGroups = moduleGroups.mapNotNull {
                it.generate()
            }
        )
    }

    private fun SolverModuleGroup.generate(): StudyModuleGroup? {
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

    private fun SolverModule.generate(): StudyModule? {
        return studyModule.copy(
            courses = courses.mapNotNull {
                it.generate()
            }
        ).let {
            if (it.courses.isEmpty()) null else it
        }
    }

    private fun SolverCourse.generate(): Course? {
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

    data class SolverStudyPlan(
        val solverSegments: List<SolverSegment>
    )

    data class SolverSegment(
        val studySegment: StudySegment,
        val moduleGroups: List<SolverModuleGroup>
    )

    data class SolverModuleGroup(
        val studyModuleGroup: StudyModuleGroup,
        val modules: List<SolverModule>,
        val chosen: BoolVar,
    )

    data class SolverModule(
        val studyModule: StudyModule,
        val courses: List<SolverCourse>,
        val chosen: BoolVar
    )

    data class SolverCourse(
        val course: Course,
        val chosen: BoolVar
    )
}
