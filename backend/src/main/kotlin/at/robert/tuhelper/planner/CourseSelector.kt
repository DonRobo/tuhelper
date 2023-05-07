package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyCourse

interface CourseSelector : Selector<StudyCourse> {
    companion object {
        fun course(course: StudyCourse): CourseSelector {
            return object : CourseSelector {
                override fun choose(from: List<StudyCourse>): List<StudyCourse> {
                    return listOf(course)
                }
            }
        }
    }

}
