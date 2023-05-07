package at.robert.tuhelper.planner

import at.robert.tuhelper.data.*
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
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

        val totalEffortVar = model.vars.singleOrNull { it.name == "total effort" }

        var studyPlan: StudyPlan? = null
        model.solver.limitTime("5s")
        var `continue` = true
        while (`continue`) {
            val solved = model.solver.solve()
            if (solved) {
                println("Found solution with effort ${totalEffortVar?.asIntVar()?.value}")
                studyPlan = configurationStudyPlan.generate()
            } else
                `continue` = false
        }
        if (studyPlan == null) {
            error("No solution found: " + model.solver.contradictionException)
        } else {
            println("Solution found")
        }

        return studyPlan
    }

    private fun generateForModel(model: Model): SolverStudyPlan {
        var id = 1
        fun id() = id++

        fun configureCourse(
            moduleGroupChosen: BoolVar,
            course: StudyCourse,
            conf: CourseConfig
        ): SolverCourse {
            val effortMultiplier = conf.effortMultiplier * effortScale
            val ects = conf.ects ?: course.ects?.toFloat() ?: 0f
            val scaledEcts = ects.toScaledEcts()
            val effort = max((ects * effortMultiplier).roundToInt(), 1)

            val chosen = model.boolVar("chose ${course.name}" + id())
            val ectsChosen = model.intVar("ectsChosen for ${course.name}" + id(), intArrayOf(0, scaledEcts))
            val effortChosen = model.intVar("effortChosen for ${course.name}" + id(), intArrayOf(0, effort))

            if (conf.required) {
                moduleGroupChosen.eq(1).imp(chosen.eq(1)).post()
            }

            ectsChosen.eq(chosen.mul(scaledEcts)).post()
            effortChosen.eq(chosen.mul(effort)).post()

            return SolverCourse(
                course.copy(ects = ects.toBigDecimal()),
                chosen = chosen,
                ectsChosen = ectsChosen,
                effortChosen = effortChosen,
                required = conf.required,
            )
        }

        fun configureModule(
            moduleGroupChosen: BoolVar,
            module: StudyModule,
            moduleConfig: ModuleConfig
        ): SolverModule? {
            require(!moduleConfig.required || !moduleConfig.excluded)
            if (moduleConfig.excluded) return null

            val courses = moduleConfig.configureSubs(module.courses) { courseConfig, obj ->
                configureCourse(
                    moduleGroupChosen = moduleGroupChosen,
                    course = obj,
                    conf = courseConfig.apply {
                        if (moduleConfig.required)
                            required = true
                    }
                )
            }

            val ectsChosen = model.intVar("ectsChosen for ${module.name}" + id(),
                courses.sumOf { it.ectsChosen.lb },
                courses.sumOf { it.ectsChosen.ub }
            )
            model.sum(courses.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()

            if (moduleConfig.maxEcts != null)
                model.arithm(ectsChosen, "<=", moduleConfig.maxEcts.toScaledEcts()).post()

            return SolverModule(
                module,
                courses,
                ectsChosen = ectsChosen,
                required = moduleConfig.required
            )
        }

        fun configureModuleGroup(
            moduleGroup: StudyModuleGroup,
            conf: ModuleGroupConfig
        ): SolverModuleGroup {
            val chosen = model.boolVar("chose ${moduleGroup.name}" + id())

            val solverModules = conf.configureSubs(moduleGroup.modules) { conf, obj ->
                configureModule(chosen, obj, conf)
            }
            val ectsChosen = model.intVar("ectsChosen for module group ${moduleGroup.name}" + id(),
                solverModules.sumOf { it.ectsChosen.lb },
                solverModules.sumOf { it.ectsChosen.ub }
            )
            model.sum(solverModules.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()

            solverModules.forEach { solverModule ->
                solverModule.courses.forEach { solverCourse ->
                    solverCourse.chosen.eq(1).imp(chosen.eq(1)).post()
                }
            }

            return SolverModuleGroup(
                moduleGroup,
                solverModules,
                chosen,
                ectsChosen,
            )
        }

        fun configureSegment(segment: StudySegment, conf: SegmentConfig): SolverSegment {
            val solverModuleGroups = conf.configureSubs(segment.moduleGroups) { conf, obj ->
                configureModuleGroup(obj, conf)
            }

            val ectsChosen = model.intVar("ectsChosen for segment ${segment.name}" + id(),
                solverModuleGroups.sumOf { it.ectsChosen.lb },
                solverModuleGroups.sumOf { it.ectsChosen.ub }
            )

            model.sum(solverModuleGroups.map { it.ectsChosen }.toTypedArray(), "=", ectsChosen).post()
            model.sum(solverModuleGroups.map { it.chosen }.toTypedArray(), "=", 1).post()

            val requiredEcts = (conf.requiredEcts ?: segment.ects?.toFloat()).toScaledEcts()
            model.arithm(ectsChosen, ">=", requiredEcts).post()

            return SolverSegment(
                segment,
                solverModuleGroups,
                ectsChosen,
            )
        }

        return SolverStudyPlan(
            config.configureSubs(studyData.segments) { conf, segment ->
                configureSegment(segment, conf)
            },
        ).also { studyPlan ->
            val moduleGroups = studyPlan.solverSegments.flatMap { solverSegment ->
                solverSegment.moduleGroups
            }
            val courses = moduleGroups.flatMap { moduleGroup ->
                moduleGroup.modules.flatMap { module ->
                    module.courses
                }
            }

            courses.groupBy { it.course.name }.filter { it.value.size > 1 }.forEach { (_, courses) ->
                println("Course ${courses.first().course.name} has ${courses.size} instances")
                model.sum(courses.map { it.chosen }.toTypedArray(), "<=", 1).post()
            }
            moduleGroups.groupBy { it.studyModuleGroup.letter }.filter { it.key != null && it.value.size > 1 }
                .forEach { (_, moduleGroups) ->
                    println("Module group ${moduleGroups.first().studyModuleGroup.name} has ${moduleGroups.size} instances")
                    model.sum(moduleGroups.map { it.chosen }.toTypedArray(), "<=", 1).post()
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
        println("Total ects: " + courses.sumOf { it.ectsChosen.value / ectsScale.toDouble() })
        println("Total effort: " + courses.sumOf { it.effortChosen.value.toDouble() })

        return StudyPlan(
            this.solverSegments.map {
                it.generate()
            }
        )
    }

    private fun SolverSegment.generate(): PlannedStudySegment {
        return PlannedStudySegment(
            id = studySegment.id,
            name = studySegment.name,
            requiredEcts = studySegment.ects?.toFloat() ?: 0f,
            moduleGroups = moduleGroups.mapNotNull {
                it.generate()
            }
        )
    }

    private fun SolverModuleGroup.generate(): PlannedModuleGroup? {
        return if (chosen.value == 1) {
            PlannedModuleGroup(
                id = studyModuleGroup.id,
                name = studyModuleGroup.name,
                modules.mapNotNull {
                    it.generate()
                }
            )
        } else {
            null
        }
    }

    private fun SolverModule.generate(): PlannedStudyModule? {
        return PlannedStudyModule(
            id = this.studyModule.id,
            name = this.studyModule.name,
            courses = courses.mapNotNull {
                it.generate()
            },
            required = this.required,
        ).let {
            if (it.courses.isEmpty()) null else it
        }
    }

    private fun SolverCourse.generate(): PlannedStudyCourse? {
        return if (chosen.value == 1) PlannedStudyCourse(
            id = this.course.id,
            name = this.course.name,
            ects = this.ectsChosen.value / ectsScale,
            effort = this.effortChosen.value / effortScale,
            required = this.required,
        ) else null
    }

    fun allModuleGroups(): List<StudyModuleGroup> {
        return studyData.segments.flatMap { segment ->
            segment.moduleGroups
        }.distinctBy { it.id }
    }

    fun allCourses(): List<StudyCourse> {
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
        val ectsChosen: IntVar,
        val required: Boolean,
    )

    data class SolverCourse(
        val course: StudyCourse,
        val chosen: BoolVar,
        val ectsChosen: IntVar,
        val effortChosen: IntVar,
        val required: Boolean,
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
