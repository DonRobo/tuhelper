package at.robert.tuhelper.tugraz

data class FetchedStudySegment(
    val studySegment: TUGrazClientStudySegment,
)

data class FetchedModuleGroup(
    val moduleGroup: TUGrazClientStudySegmentModuleGroup,
    val fetchedStudySegment: FetchedStudySegment,
)

data class FetchedModule(
    val module: TUGrazClientStudyModule,
    val fetchedModuleGroup: FetchedModuleGroup,
)

data class FetchedCourse(
    val course: TUGrazClientStudyCourse,
    val fetchedModule: FetchedModule,
)
