package at.robert.tuhelper.planner

import at.robert.tuhelper.data.Course

class ModuleConfig {

    var required: Boolean = false
    var excluded: Boolean = false

    val courses = mutableListOf<Pair<String, CourseConfig>>()
    val coursesToAdd = mutableListOf<Pair<Course, CourseConfig>>()

    fun addCourses(courses: List<Course>, block: CourseConfig.(Course) -> Unit) {
        coursesToAdd.addAll(courses.map { it to CourseConfig().apply { block(it) } })
    }

    fun addCourse(courseName: String, block: CourseConfig.() -> Unit) {
        val config = CourseConfig()
        config.block()
        courses.add(courseName to config)
    }

    var maxEcts: Float? = null
}
