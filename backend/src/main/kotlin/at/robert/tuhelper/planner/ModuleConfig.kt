package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyCourse

class ModuleConfig : SelectorBasedConfig<StudyCourse, CourseConfig> {

    var required: Boolean = false
    var excluded: Boolean = false

    private val courseConfigs = mutableListOf<Pair<CourseSelector, CourseConfig>>()

    fun addCourses(courses: List<StudyCourse>, block: CourseConfig.() -> Unit) {
        courses.forEach {
            addCourse(it, block)
        }
    }

    private fun addCourse(course: StudyCourse, block: CourseConfig.() -> Unit) {
        val config = createSubConfig(course)
        config.block()
        courseConfigs.add(CourseSelector.course(course) to config)
    }

    fun addCourse(courseName: String, block: CourseConfig.() -> Unit) {
        val course = StudyCourse(-1, courseName, null)
        addCourse(course, block)
    }

    var maxEcts: Float? = null
    override var handleDefaults: DefaultHandling = DefaultHandling.ADD
    override val subConfigs: List<Pair<Selector<StudyCourse>, CourseConfig>>
        get() = courseConfigs

    override fun createSubConfig(obj: StudyCourse): CourseConfig {
        return CourseConfig()
    }
}
