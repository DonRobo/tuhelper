package at.robert.tuhelper.planner

import at.robert.tuhelper.data.Course

class ModuleConfig {

    var required: Boolean = false
    var excluded: Boolean = false

    val courses = mutableListOf<Pair<String, CourseConfig>>()
    val coursesToAdd = mutableListOf<Course>()
    fun required() {
        required = true
    }

    fun addCourses(courses: List<Course>) {
        coursesToAdd.addAll(courses)
    }

    fun addCourse(courseName: String, block: CourseConfig.() -> Unit) {
        val config = CourseConfig()
        config.block()
        courses.add(courseName to config)
    }

    fun excluded() {
        excluded = true
    }

    var maxEcts: Int? = null
}
