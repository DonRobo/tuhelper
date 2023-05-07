package at.robert.tuhelper.tugraz

import at.robbert.tuhelper.jooq.enums.JStudyType
import at.robbert.tuhelper.jooq.tables.records.JStudyRecord
import at.robert.tuhelper.data.*
import at.robert.tuhelper.log
import at.robert.tuhelper.repository.StudyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StudyService(
    private val tuGrazClient: TUGrazClient,
    private val studyRepository: StudyRepository,
) {
    fun updateAlLStudies() {
        val studies = runBlocking { tuGrazClient.fetchStudies() }

        val inserted = studyRepository.cleanAndInsertStudies(studies.map { study ->
            JStudyRecord().apply {
                this.name = study.name
                this.number = study.number
                this.ects = study.ects
                this.type = JStudyType.valueOf(study.type.name)
                this.tugOnlineId = study.studyId
            }
        })
        log.debug("Inserted $inserted studies")
    }

    @Transactional
    fun getStudies(): List<Study> {
        fun studiesFromDb(): List<Study> {
            return studyRepository.fetchAllStudies().map { study ->
                Study(
                    number = study.number,
                    name = study.name,
                    ects = study.ects,
                    type = StudyType.valueOf(study.type.name)
                )
            }
        }

        val studies = studiesFromDb()
        return studies.ifEmpty {
            log.info("No studies found in database, fetching from TUGraz")
            updateAlLStudies()
            studiesFromDb()
        }
    }

    @Transactional
    fun getStudyData(studyNumber: String): StudyData {
        if (studyRepository.countStudies() == 0) {
            updateAlLStudies()
        }

        val alreadyFetched = studyRepository.fetchedDate(studyNumber)
        if (alreadyFetched == null) {
            val studyId = studyRepository.studyId(studyNumber)
            fetchAndInsertStudyData(studyId, studyNumber)
        }

        return StudyData(
            studyRepository.fetchStudy(studyNumber),
            studyRepository.fetchStudySegments(studyNumber).map {
                StudySegment(
                    it.id,
                    it.name,
                    it.ects,
                    studyRepository.fetchStudyModuleGroups(studyNumber, it.id).map { moduleGroupRecord ->
                        StudyModuleGroup(
                            id = moduleGroupRecord.id,
                            name = moduleGroupRecord.name,
                            modules = studyRepository.fetchModules(moduleGroupRecord.id).map { moduleRecord ->
                                StudyModule(
                                    id = moduleRecord.id,
                                    name = moduleRecord.name,
                                    courses = studyRepository.fetchCourses(moduleRecord.id).map { courseRecord ->
                                        StudyCourse(
                                            id = courseRecord.id,
                                            name = courseRecord.name,
                                            ects = courseRecord.ects,
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private fun fetchAndInsertStudyData(studyId: Int, studyNumber: String) {
        val fetchedCourses = runBlocking {
            val segments = tuGrazClient.getStudySegments(studyId).map { FetchedStudySegment(it) }
            val segmentModuleGroups = segments.map { segment ->
                async {
                    tuGrazClient.getStudySegmentModuleGroups(segment.studySegment).map {
                        FetchedModuleGroup(it, segment)
                    }
                }
            }
            val modules = segmentModuleGroups.map {
                async {
                    it.await().flatMap { moduleGroup ->
                        tuGrazClient.getStudyModules(moduleGroup.moduleGroup).map {
                            FetchedModule(it, moduleGroup)
                        }
                    }
                }
            }
            val courses = modules.map {
                async {
                    it.await().flatMap { module ->
                        tuGrazClient.getCourses(module.module).map {
                            FetchedCourse(it, module)
                        }
                    }
                }
            }
            courses.awaitAll().flatten()
        }
        log.info("Fetched ${fetchedCourses.size} courses for study $studyNumber")
        studyRepository.cleanAndInsertStudyData(studyNumber, fetchedCourses)
    }
}
