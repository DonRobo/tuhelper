package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudySegment

class StudyPlannerConfig {

    val segmentsConfigs = mutableListOf<Pair<SegmentSelector, SegmentConfig>>()

    fun segment(segmentName: String, block: SegmentConfig.() -> Unit) {
        val config = SegmentConfig()
        config.block()
        segmentsConfigs.add(SegmentSelector.nameContains(segmentName) to config)
    }

    fun addSegment(segmentName: String, block: SegmentConfig.() -> Unit) {
        val config = SegmentConfig()
        config.block()
        segmentsConfigs.add(SegmentSelector.newSegment(StudySegment(-1, segmentName, null, emptyList())) to config)
    }
}
