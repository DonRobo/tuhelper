package at.robert.tuhelper.tugraz

import at.robbert.tuhelper.jooq.enums.JStudyType
import at.robbert.tuhelper.jooq.tables.records.JStudyRecord
import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyType
import at.robert.tuhelper.log
import at.robert.tuhelper.repository.StudyRepository
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

@Service
class StudyService(
    private val tuGrazClient: TUGrazClient,
    private val studyRepository: StudyRepository,
) {
    fun updateAlLStudies() {
        val studies = runBlocking { tuGrazClient.fetchStudies() }
        log.debug("Fetched ${studies.size} studies")

        val inserted = studyRepository.cleanAndInsert(studies.map { study ->
            JStudyRecord().apply {
                this.name = study.name
                this.number = study.number
                this.ects = study.ects
                this.type = JStudyType.valueOf(study.type.name)
            }
        })
        log.debug("Inserted $inserted studies")
    }

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
}
