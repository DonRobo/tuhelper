package at.robert.tuhelper.repository

import at.robbert.tuhelper.jooq.Tables
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class EffortRepository(
    private val ctx: DSLContext,
) {

    private val ce = Tables.COURSE_EFFORT.`as`("ce")
    fun getAllEffortMultipliers(): Map<String, Double> {
        return ctx.select(ce.COURSE_NAME, ce.EFFORT_MULTIPLIER)
            .from(ce)
            .fetchMap(ce.COURSE_NAME, ce.EFFORT_MULTIPLIER)
    }

    fun upsertEffort(courseName: String, effort: Double) {
        ctx.insertInto(ce)
            .columns(ce.COURSE_NAME, ce.EFFORT_MULTIPLIER)
            .values(courseName, effort)
            .onDuplicateKeyUpdate()
            .set(ce.EFFORT_MULTIPLIER, effort)
            .execute()
    }

    fun getEffort(courseName: String): Double? {
        return ctx.select(ce.EFFORT_MULTIPLIER)
            .from(ce)
            .where(ce.COURSE_NAME.eq(courseName))
            .fetchOne(ce.EFFORT_MULTIPLIER)
    }


}
