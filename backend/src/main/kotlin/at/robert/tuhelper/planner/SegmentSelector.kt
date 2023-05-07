package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudySegment

interface SegmentSelector : Selector<StudySegment> {

    fun chooseSegment(segmentList: List<StudySegment>): StudySegment

    override fun choose(from: List<StudySegment>): List<StudySegment> {
        return listOf(chooseSegment(from))
    }

    companion object {
        fun nameContains(string: String): SegmentSelector {
            return object : SegmentSelector {
                override fun chooseSegment(segmentList: List<StudySegment>): StudySegment {
                    return segmentList.singleOrNull { it.name.contains(string) }
                        ?: error("No segment with name containing $string found: ${segmentList.map { it.name }}")
                }
            }
        }

        fun newSegment(studySegment: StudySegment): SegmentSelector {
            return object : SegmentSelector {
                override fun chooseSegment(segmentList: List<StudySegment>): StudySegment {
                    return studySegment
                }
            }
        }

    }
}
