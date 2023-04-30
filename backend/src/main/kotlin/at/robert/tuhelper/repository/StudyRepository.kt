package at.robert.tuhelper.repository

import at.robbert.tuhelper.jooq.Tables
import at.robbert.tuhelper.jooq.tables.records.JStudyRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class StudyRepository(
    private val ctx: DSLContext,
) {

    private val s = Tables.STUDY.`as`("s")

    @Transactional
    fun cleanAndInsert(studies: List<JStudyRecord>): Int {
        ctx.deleteFrom(s).execute()

        return ctx.batchInsert(studies).execute().sum()
    }

    fun fetchAllStudies(): List<JStudyRecord> {
        return ctx.selectFrom(s).fetch()
    }

}
