package at.robert.tuhelper.repository

import at.robbert.tuhelper.jooq.Tables
import at.robbert.tuhelper.jooq.tables.records.JStudyRecord
import at.robbert.tuhelper.jooq.tables.records.JStudySegmentRecord
import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Repository
class StudyRepository(
    private val ctx: DSLContext,
) {

    private val s = Tables.STUDY.`as`("s")
    private val ss = Tables.STUDY_SEGMENT.`as`("ss")

    @Transactional
    fun cleanAndInsertStudies(studies: List<JStudyRecord>): Int {
        ctx.deleteFrom(s).execute()

        return ctx.batchInsert(studies).execute().sum()
    }

    fun fetchAllStudies(): List<JStudyRecord> {
        return ctx.selectFrom(s).orderBy(s.NUMBER).fetch()
    }

    fun fetchedDate(studyNumber: String): Timestamp? {
        return ctx.select(s.FETCHED)
            .from(s)
            .where(s.NUMBER.eq(studyNumber))
            .fetchOneInto(Timestamp::class.java)
    }

    fun fetchStudy(studyNumber: String): Study {
        return ctx.selectFrom(s)
            .where(s.NUMBER.eq(studyNumber))
            .fetchOne()!!.let {
                Study(
                    number = it.number,
                    name = it.name,
                    ects = it.ects,
                    type = StudyType.valueOf(it.type.name)
                )
            }
    }

    fun fetchStudySegments(studyNumber: String): List<String> {
        return ctx.select(ss.NAME)
            .from(ss)
            .where(ss.STUDY_NUMBER.eq(studyNumber))
            .orderBy(ss.ID)
            .fetchInto(String::class.java)
    }

    fun studyId(studyNumber: String): Int {
        return ctx.select(s.TUG_ONLINE_ID)
            .from(s)
            .where(s.NUMBER.eq(studyNumber))
            .fetchOneInto(Int::class.java)!!
    }

    @Transactional
    fun cleanAndInsertStudySegments(studyNumber: String, segments: List<JStudySegmentRecord>): Int {
        ctx.deleteFrom(ss).where(ss.STUDY_NUMBER.eq(studyNumber)).execute()
        val inserted = ctx.batchInsert(segments.onEach {
            require(it.studyNumber == studyNumber) {
                "Study number of segment ${it.name} is not $studyNumber"
            }
        }).execute().sum()
        ctx.update(s)
            .set(s.FETCHED, DSL.currentLocalDateTime())
            .where(s.NUMBER.eq(studyNumber))
            .execute()

        return inserted
    }

    fun countStudies(): Int {
        return ctx.selectCount().from(s).fetchOneInto(Int::class.java)!!
    }

}
