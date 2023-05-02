package at.robert.tuhelper.tugraz

import at.robbert.tuhelper.jooq.enums.JStudyType
import at.robbert.tuhelper.jooq.tables.records.JStudyRecord
import at.robbert.tuhelper.jooq.tables.records.JStudySegmentRecord
import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyData
import at.robert.tuhelper.data.StudyType
import at.robert.tuhelper.log
import at.robert.tuhelper.repository.StudyRepository
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
        log.debug("Fetched ${studies.size} studies")

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
            studyRepository.fetchStudySegments(studyNumber)
        )
    }

    private fun fetchAndInsertStudyData(studyId: Int, studyNumber: String) {
        val segments = runBlocking {
            tuGrazClient.getStudySegments(studyId)
        }.map {
            JStudySegmentRecord().apply {
                this.name = it.name
                this.ects = it.ects
                this.studyNumber = studyNumber
            }
        }
        studyRepository.cleanAndInsertStudySegments(studyNumber, segments)
    }
}
