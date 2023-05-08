package at.robert.tuhelper.planner

import at.robert.tuhelper.data.*
import at.robert.tuhelper.log
import com.google.ortools.Loader
import com.google.ortools.sat.*
import com.google.ortools.util.Domain
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class StudyPlanner(
    private val studyData: StudyData,
    private val effortData: Map<String, Double>,
) {
    init {
        Loader.loadNativeLibraries()
    }

    private var config = StudyPlannerConfig()
    private val effortScale = 100f
    private val ectsScale = 10f

    fun configure(block: StudyPlannerConfig.() -> Unit) {
        val config = StudyPlannerConfig()
        config.block()
        this.config = config
    }

    fun solve(): StudyPlan {
        val model = CpModel()

        val (configurationStudyPlan, totalEffortVar) = generateForModel(model)

        val courses = configurationStudyPlan.solverSegments
            .flatMap { it.moduleGroups }
            .flatMap { it.modules }
            .flatMap { it.courses }

        val solver = CpSolver()
        var studyPlan: StudyPlan? = null
        var `continue` = true
        while (`continue`) {
            val solved = solver.solve(model, object : CpSolverSolutionCallback() {
                override fun onSolutionCallback() {
                    log.debug("Solution found: ${objectiveValue()}")
                }
            })
            when (solved) {
                CpSolverStatus.FEASIBLE -> {
                    log.warn("Sub-optimal solution found")
                    studyPlan = configurationStudyPlan.generate(solver)
                }

                CpSolverStatus.OPTIMAL -> {
                    log.info("Optimal solution found: ${solver.objectiveValue()}")
                    studyPlan = configurationStudyPlan.generate(solver)
                    `continue` = false
                }

                else -> {
                    error("No solution found: $solved")
                }
            }
        }
        if (studyPlan == null) {
            error("No solution found")
        } else {
            log.info("Solution found")
        }

        return studyPlan
    }

    private fun generateForModel(model: CpModel): Pair<SolverStudyPlan, IntVar> {
        var id = 1
        fun id() = id++

        fun domainOf(vararg values: Number) = Domain.fromValues(values.map { it.toLong() }.toLongArray())

        fun configureCourse(
            moduleGroupChosen: BoolVar,
            course: StudyCourse,
            conf: CourseConfig
        ): SolverCourse {
            val effortMultiplier = effortData.getOrDefault(course.actualName, 1.0) * effortScale
            if (effortData.containsKey(course.actualName)) {
                log.debug("Effort multiplier for ${course.name} is $effortMultiplier")
            }
            val ects = conf.ects ?: course.ects?.toFloat() ?: 0f
            val scaledEcts = ects.toScaledEcts()
            val effort = max((ects * effortMultiplier).roundToInt(), 1)

            val chosen = model.newBoolVar("chose ${course.name}" + id())
            val ectsChosen = model.newIntVarFromDomain(domainOf(0, scaledEcts), "ectsChosen for ${course.name}" + id())
            val effortChosen = model.newIntVarFromDomain(domainOf(0, effort), "effortChosen for ${course.name}" + id())

            if (conf.required) {
                model.addEquality(chosen, 1).onlyEnforceIf(moduleGroupChosen)
            }

            model.addEquality(ectsChosen, LinearExpr.term(chosen, scaledEcts.toLong()))
            model.addEquality(effortChosen, LinearExpr.term(chosen, effort.toLong()))

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

            val ectsChosen = model.newIntVar(
                courses.sumOf { it.ectsChosen.domain.min() },
                courses.sumOf { it.ectsChosen.domain.max() },
                "ectsChosen for ${module.name}" + id()
            )
            model.addEquality(ectsChosen, LinearExpr.sum(courses.map { it.ectsChosen }.toTypedArray()))

            if (moduleConfig.maxEcts != null)
                model.addLessOrEqual(ectsChosen, moduleConfig.maxEcts.toScaledEcts().toLong())

            return SolverModule(
                module,
                courses,
                ectsChosen = ectsChosen,
                required = moduleConfig.required
            )
        }

        fun configureModuleGroup(
            moduleGroup: StudyModuleGroup,
            moduleGroupConfig: ModuleGroupConfig
        ): SolverModuleGroup {
            val chosen = model.newBoolVar("chose ${moduleGroup.name}" + id())

            val solverModules = moduleGroupConfig.configureSubs(moduleGroup.modules) { moduleConfig, obj ->
                configureModule(chosen, obj, moduleConfig)
            }
            val ectsChosen = model.newIntVar(
                solverModules.sumOf { it.ectsChosen.domain.min() },
                solverModules.sumOf { it.ectsChosen.domain.max() },
                "ectsChosen for module group ${moduleGroup.name}" + id(),
            )
            model.addEquality(ectsChosen, LinearExpr.sum(solverModules.map { it.ectsChosen }.toTypedArray()))

            solverModules.forEach { solverModule ->
                solverModule.courses.forEach { solverCourse ->
                    model.addImplication(solverCourse.chosen, chosen)
                }
            }

            return SolverModuleGroup(
                moduleGroup,
                solverModules,
                chosen,
                ectsChosen,
            )
        }

        fun configureSegment(segment: StudySegment, segmentConfig: SegmentConfig): SolverSegment {
            val solverModuleGroups = segmentConfig.configureSubs(segment.moduleGroups) { moduleGroupConfig, obj ->
                configureModuleGroup(obj, moduleGroupConfig)
            }

            val ectsChosen = model.newIntVar(
                solverModuleGroups.sumOf { it.ectsChosen.domain.min() },
                solverModuleGroups.sumOf { it.ectsChosen.domain.max() },
                "ectsChosen for segment ${segment.name}" + id(),
            )

            model.addEquality(ectsChosen, LinearExpr.sum(solverModuleGroups.map { it.ectsChosen }.toTypedArray()))
            model.addExactlyOne(solverModuleGroups.map { it.chosen }.toTypedArray())

            val requiredEcts = (segmentConfig.requiredEcts ?: segment.ects?.toFloat()).toScaledEcts()
            model.addGreaterOrEqual(ectsChosen, requiredEcts.toLong())

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
        ).let { studyPlan ->
            val moduleGroups = studyPlan.solverSegments.flatMap { solverSegment ->
                solverSegment.moduleGroups
            }
            val courses = moduleGroups.flatMap { moduleGroup ->
                moduleGroup.modules.flatMap { module ->
                    module.courses
                }
            }

            courses.groupBy { it.course.actualName }.filter { it.value.size > 1 }.forEach { (_, courses) ->
                log.trace("Course ${courses.first().course.name} has ${courses.size} instances")
                model.addAtMostOne(courses.map { it.chosen }.toTypedArray())
            }
            moduleGroups.groupBy { it.studyModuleGroup.letter }.filter { it.key != null && it.value.size > 1 }
                .forEach { (_, moduleGroups) ->
                    log.debug("Module group ${moduleGroups.first().studyModuleGroup.name} has ${moduleGroups.size} instances")
                    model.addAtMostOne(moduleGroups.map { it.chosen }.toTypedArray())
                }

            if (config.combineCourseTypes) {
                val regex = Regex("(.*?)(?: \\(.*\\))?, [A-Z]{2}")
                courses.groupBy {
                    val match = regex.matchEntire(it.course.actualName)
                    if (match != null) {
                        match.groupValues[1].lowercase()
                    } else {
                        null
                    }
                }.filterKeys { it != null }.forEach { (_, courses) ->
                    val combinedByName = courses.groupBy { it.course.actualName }
                    if (combinedByName.size == 1) return@forEach
                    val vars = combinedByName.map { (_, sameCourses) ->
                        if (sameCourses.size == 1) return@map sameCourses.first().chosen

                        val anyChosen = model.newBoolVar("chose any of ${sameCourses.first().course.actualName}" + id())
                        require(sameCourses.isNotEmpty()) {
                            "No courses in group"
                        }
                        model.addEquality(anyChosen, LinearExpr.sum(sameCourses.map { it.chosen }.toTypedArray()))
                        anyChosen
                    }
                    val first = vars.first()
                    vars.drop(1).forEach {
                        model.addEquality(first, it)
                    }
                }
            }

            val effort = model.newIntVar(
                courses.sumOf { it.effortChosen.domain.min() },
                courses.sumOf { it.effortChosen.domain.max() },
                "total effort",
            )

            model.addEquality(effort, LinearExpr.sum(courses.map { it.effortChosen }.toTypedArray()))

            model.minimize(effort)

            studyPlan to effort
        }
    }

    private fun SolverStudyPlan.generate(solver: CpSolver): StudyPlan {
        fun IntVar.value(): Long = solver.value(this)
        val courses = this.solverSegments.flatMap {
            it.moduleGroups.flatMap {
                it.modules.flatMap {
                    it.courses
                }
            }
        }.filter {
            it.chosen.value() == 1L
        }

        log.debug(courses.joinToString { it.course.name })
        log.debug("Total ects: " + courses.sumOf { it.ectsChosen.value() / ectsScale.toDouble() })
        log.debug("Total effort: " + courses.sumOf { it.effortChosen.value() / effortScale.toDouble() })

        return StudyPlan(
            this.solverSegments.map {
                it.generate(solver)
            }
        )
    }

    private fun SolverSegment.generate(solver: CpSolver): PlannedStudySegment {
        return PlannedStudySegment(
            id = studySegment.id,
            name = studySegment.name,
            requiredEcts = studySegment.ects?.toFloat() ?: 0f,
            moduleGroups = moduleGroups.mapNotNull {
                it.generate(solver)
            }
        )
    }

    private fun SolverModuleGroup.generate(solver: CpSolver): PlannedModuleGroup? {
        return if (solver.booleanValue(chosen)) {
            PlannedModuleGroup(
                id = studyModuleGroup.id,
                name = studyModuleGroup.name,
                modules.mapNotNull {
                    it.generate(solver)
                }
            )
        } else {
            null
        }
    }

    private fun SolverModule.generate(solver: CpSolver): PlannedStudyModule? {
        return PlannedStudyModule(
            id = this.studyModule.id,
            name = this.studyModule.name,
            courses = courses.mapNotNull {
                it.generate(solver)
            },
            required = this.required,
        ).let {
            if (it.courses.isEmpty()) null else it
        }
    }

    private fun SolverCourse.generate(solver: CpSolver): PlannedStudyCourse? {
        return if (solver.booleanValue(chosen)) PlannedStudyCourse(
            id = this.course.id,
            name = this.course.name,
            ects = solver.value(this.ectsChosen) / ectsScale,
            effort = solver.value(this.effortChosen) / effortScale,
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
