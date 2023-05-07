package at.robert.tuhelper.planner

import at.robert.tuhelper.data.*
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class StudyPlanner(
    private val studyData: StudyData
) {
    private var config = StudyPlannerConfig()
    private val effortScale = 100f
    private val ectsScale = 10f

    fun configure(block: StudyPlannerConfig.() -> Unit) {
        val config = StudyPlannerConfig()
        config.block()
        this.config = config
    }

    fun solve(): StudyPlan {
        val model = Model("Master")

        val configurationStudyPlan = generateForModel(model)

        val result = model.solver.solve()
        if (!result) {
            error("No solution found: " + model.solver.contradictionException)
        } else {
            println("Solution found")
        }

        return configurationStudyPlan.generate()
    }

    private fun generateForModel(model: Model): SolverStudyPlan {
        fun configureCourse(
            course: Course,
            conf: CourseConfig
        ): SolverCourse {
            val effortMultiplier = conf.effortMultiplier * effortScale
            val ects = conf.ects ?: course.ects?.toFloat() ?: 0f
            val scaledEcts = ects.toScaledEcts()
            val effort = (ects * effortMultiplier).roundToInt()

            val chosen = model.boolVar("chose ${course.name}")
            val ectsChosen = model.intVar("ectsChosen for ${course.name}", intArrayOf(0, scaledEcts))
            val effortChosen = model.intVar("effortChosen for ${course.name}", intArrayOf(0, effort))

            ectsChosen.eq(chosen.mul(scaledEcts)).post()
            effortChosen.eq(chosen.mul(effort)).post()

            return SolverCourse(
                course.copy(ects = ects.toBigDecimal()),
                chosen = chosen,
                ectsChosen = ectsChosen,
                effortChosen = effortChosen,
            )
        }

        fun configureCourse(name: String, conf: CourseConfig) =
            configureCourse(
                Course(
                    id = -1,
                    name = name,
                    ects = conf.ects?.toBigDecimal()
                ),
                conf
            )

        fun configureModule(
            moduleOptions: List<StudyModule>,
            selector: ModuleSelector,
            conf: ModuleConfig
        ): SolverModule? {
            require(!conf.required || !conf.excluded)
            if (conf.excluded) return null
            val module = selector.chooseModule(moduleOptions) ?: return null

            //FIXME check that they aren't excluded or some stuff
            val coursesToAdd = module.courses
            conf.addCourses(coursesToAdd) {
            }

            val courses = conf.courses.map { (name, courseConf) ->
                configureCourse(name, courseConf.apply { this.required = true })
            } + conf.coursesToAdd.map { (course, courseConf) ->
                configureCourse(course, courseConf.apply { this.required = true })
            }
            if (conf.required) {
                courses.forEach {
                    it.chosen.eq(1).post()
                }
            }
            val ectsChosen = model.intVar("ectsChosen for ${module.name}",
                courses.sumOf { it.ectsChosen.lb },
                courses.sumOf { it.ectsChosen.ub }
            )
            model.sum(courses.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()

            if (conf.maxEcts != null)
                model.arithm(ectsChosen, "<=", conf.maxEcts.toScaledEcts()).post()

            return SolverModule(
                module,
                courses,
                ectsChosen = ectsChosen
            )
        }

        fun configureModuleGroup(
            moduleGroupOptions: List<StudyModuleGroup>,
            selector: ModuleGroupSelector,
            conf: ModuleGroupConfig
        ): SolverModuleGroup {
            val moduleGroup = selector.chooseModuleGroup(moduleGroupOptions)
            val solverModules = conf.modules.mapNotNull { (selector, conf) ->
                configureModule(moduleGroup.modules, selector, conf)
            }
            val ectsChosen = model.intVar("ectsChosen for module group ${moduleGroup.name}",
                solverModules.sumOf { it.ectsChosen.lb },
                solverModules.sumOf { it.ectsChosen.ub }
            )
            model.sum(solverModules.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()

            val chosen = model.boolVar("chose ${moduleGroup.name}")
            solverModules.forEach { solverModule ->
                solverModule.courses.forEach { solverCourse ->
                    solverCourse.chosen.eq(0).iff(chosen.eq(0)).post()
                }
            }

            return SolverModuleGroup(
                moduleGroup,
                solverModules,
                chosen,
                ectsChosen,
            )
        }

        fun configureSegment(selector: SegmentSelector, conf: SegmentConfig): SolverSegment {
            val segment = selector.chooseSegment(studyData.segments)
            val solverModuleGroups = conf.moduleGroups.map { (selector, conf) ->
                configureModuleGroup(segment.moduleGroups, selector, conf)
            }

            val ectsChosen = model.intVar("ectsChosen for segment ${segment.name}",
                solverModuleGroups.sumOf { it.ectsChosen.lb },
                solverModuleGroups.sumOf { it.ectsChosen.ub }
            )

            model.sum(solverModuleGroups.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()
            model.sum(solverModuleGroups.map { it.chosen }.toTypedArray(), "=", 1).post()

            val requiredEcts = (conf.requiredEcts ?: segment.ects?.toFloat()).toScaledEcts()
            model.arithm(ectsChosen, ">=", requiredEcts).post()

            println("Required ects for ${segment.name}: $requiredEcts")
            println("Possible ects from module groups: ${solverModuleGroups.sumOf { it.ectsChosen.ub } / ectsScale}")
            println("Possible cts from modules: ${solverModuleGroups.sumOf { it.modules.sumOf { it.ectsChosen.ub } } / ectsScale}")
            println("Possible cts from courses: ${solverModuleGroups.sumOf { it.modules.sumOf { it.courses.sumOf { it.ectsChosen.ub } } } / ectsScale}")

            return SolverSegment(
                segment,
                solverModuleGroups,
                ectsChosen,
            )
        }

        return SolverStudyPlan(
            config.segmentsConfigs.map { (selector, conf) ->
                configureSegment(selector, conf)
            }
        ).also { studyPlan ->
            val courses = studyPlan.solverSegments.flatMap { solverSegment ->
                solverSegment.moduleGroups.flatMap { moduleGroup ->
                    moduleGroup.modules.flatMap { module ->
                        module.courses
                    }
                }
            }
            val effort = model.intVar(
                "total effort",
                courses.sumOf { it.effortChosen.lb },
                courses.sumOf { it.effortChosen.ub },
            )

            model.sum(courses.map { it.effortChosen }.toTypedArray(), "=", effort).post()

            model.setObjective(
                false,
                effort
            )
        }
    }

    private fun SolverStudyPlan.generate(): StudyPlan {
        val courses = this.solverSegments.flatMap {
            it.moduleGroups.flatMap {
                it.modules.flatMap {
                    it.courses
                }
            }
        }.filter { it.chosen.value == 1 }

        println(courses.joinToString { it.course.name })

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
        val moduleGroups: List<SolverModuleGroup>,
        val ectsChosen: IntVar,
    )

    data class SolverModuleGroup(
        val studyModuleGroup: StudyModuleGroup,
        val modules: List<SolverModule>,
        val chosen: BoolVar,
        val ectsChosen: IntVar,
    )

    data class SolverModule(
        val studyModule: StudyModule,
        val courses: List<SolverCourse>,
        val ectsChosen: IntVar
    )

    data class SolverCourse(
        val course: Course,
        val chosen: BoolVar,
        val ectsChosen: IntVar,
        val effortChosen: IntVar
    )

    private fun Float?.toScaledEcts(ceil: Boolean = true): Int {
        return if (this == null) {
            0
        } else {
            if (ceil) {
                ceil(this * ectsScale).roundToInt()
            } else {
                floor(this * ectsScale).roundToInt()
            }
        }
    }

}
