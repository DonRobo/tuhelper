package at.robert.tuhelper.planner

import at.robert.tuhelper.data.Course

class ModuleConfig {

    var required: Boolean? = null
    var excluded: Boolean? = null
    val courses = mutableListOf<CourseConfig>()
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
        courses.add(config)
    }

    fun excluded() {
        excluded = true
    }

    var maxEcts: Int? = null
}
