package at.robert.tuhelper.repository

import at.robbert.tuhelper.jooq.Tables
import at.robbert.tuhelper.jooq.tables.records.*
import at.robert.tuhelper.data.Study
import at.robert.tuhelper.data.StudyType
import at.robert.tuhelper.fetchInto
import at.robert.tuhelper.log
import at.robert.tuhelper.tugraz.FetchedCourse
import at.robert.tuhelper.tugraz.FetchedModule
import at.robert.tuhelper.tugraz.FetchedModuleGroup
import at.robert.tuhelper.tugraz.FetchedStudySegment
import org.jooq.DSLContext
import org.jooq.TableRecord
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
    private val mg = Tables.MODULE_GROUP.`as`("mg")
    private val m = Tables.MODULE.`as`("m")
    private val mgm = Tables.MODULE_GROUP_MODULE.`as`("mgm")
    private val c = Tables.COURSE.`as`("c")
    private val mc = Tables.MODULE_COURSE.`as`("mc")
    private val ssmg = Tables.STUDY_SEGMENT_MODULE_GROUP.`as`("ssmg")

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

    fun fetchStudySegments(studyNumber: String): List<JStudySegmentRecord> {
        return ctx.selectFrom(ss)
            .where(ss.STUDY_NUMBER.eq(studyNumber))
            .orderBy(ss.ID)
            .fetch()
    }

    fun studyId(studyNumber: String): Int {
        return ctx.select(s.TUG_ONLINE_ID)
            .from(s)
            .where(s.NUMBER.eq(studyNumber))
            .fetchOneInto(Int::class.java)!!
    }

    fun countStudies(): Int {
        return ctx.selectCount().from(s).fetchOneInto(Int::class.java)!!
    }

    @Transactional
    fun cleanAndInsertStudyData(studyNumber: String, courses: List<FetchedCourse>) {
        ctx.deleteFrom(ss).where(ss.STUDY_NUMBER.eq(studyNumber)).execute()

        val courseRecords = mutableMapOf<String, JCourseRecord>()
        fun FetchedCourse.record(): JCourseRecord {
            return courseRecords.getOrPut(course.name) {
                log.trace("Inserting course ${course.name}")
                JCourseRecord().apply {
                    this.name = course.name
                    this.ects = course.ects
                }.insertReturning()
            }
        }

        val moduleGroupRecords = mutableMapOf<String, JModuleGroupRecord>()
        fun FetchedModuleGroup.record(): JModuleGroupRecord {
            return moduleGroupRecords.getOrPut(moduleGroup.name) {
                log.trace("Inserting module group ${moduleGroup.name}")
                JModuleGroupRecord().apply {
                    this.name = moduleGroup.name
                    this.studyNumber = studyNumber
                }.insertReturning()
            }
        }

        val moduleRecords = mutableMapOf<String, JModuleRecord>()
        fun FetchedModule.record(): JModuleRecord {
            return moduleRecords.getOrPut(module.name) {
                log.trace("Inserting module ${module.name}")
                JModuleRecord().apply {
                    this.name = module.name
                }.insertReturning()
            }
        }

        val studySegmentRecords = mutableMapOf<String, JStudySegmentRecord>()
        fun FetchedStudySegment.record(): JStudySegmentRecord {
            return studySegmentRecords.getOrPut(studySegment.name) {
                log.trace("Inserting study segment ${studySegment.name}")
                JStudySegmentRecord().apply {
                    this.name = studySegment.name
                    this.ects = studySegment.ects
                    this.studyNumber = studyNumber
                }.insertReturning()
            }
        }

        val moduleCourseRecords = mutableMapOf<Pair<Int, Int>, JModuleCourseRecord>()
        fun moduleCourseRecord(moduleRecord: JModuleRecord, courseRecord: JCourseRecord): JModuleCourseRecord {
            return moduleCourseRecords.getOrPut(moduleRecord.id to courseRecord.id) {
                log.trace("Inserting module course ${moduleRecord.name} - ${courseRecord.name}")
                JModuleCourseRecord().apply {
                    this.moduleId = moduleRecord.id
                    this.courseId = courseRecord.id
                }.insertReturning()
            }
        }

        val studySegmentModuleGroupRecords = mutableMapOf<Pair<Int, Int>, JStudySegmentModuleGroupRecord>()
        fun studySegmentModuleGroupRecord(
            studySegmentRecord: JStudySegmentRecord,
            moduleGroupRecord: JModuleGroupRecord
        ): JStudySegmentModuleGroupRecord {
            return studySegmentModuleGroupRecords.getOrPut(studySegmentRecord.id to moduleGroupRecord.id) {
                log.trace("Inserting study segment module group ${studySegmentRecord.name} - ${moduleGroupRecord.name}")
                JStudySegmentModuleGroupRecord().apply {
                    this.studySegmentId = studySegmentRecord.id
                    this.moduleGroupId = moduleGroupRecord.id
                }.insertReturning()
            }
        }

        val moduleGroupModuleRecords = mutableMapOf<Pair<Int, Int>, JModuleGroupModuleRecord>()
        fun moduleGroupModuleRecord(
            moduleGroupRecord: JModuleGroupRecord,
            moduleRecord: JModuleRecord
        ): JModuleGroupModuleRecord {
            return moduleGroupModuleRecords.getOrPut(moduleGroupRecord.id to moduleRecord.id) {
                log.trace("Inserting module group module ${moduleGroupRecord.name} - ${moduleRecord.name}")
                JModuleGroupModuleRecord().apply {
                    moduleGroupId = moduleGroupRecord.id
                    moduleId = moduleRecord.id
                }.insertReturning()
            }
        }

        log.trace("Inserting ${courses.size} courses")
        //TODO handle already existing stuff properly
        try {
            @Suppress("UNUSED_VARIABLE")
            courses.forEach {
                val courseRecord = it.record()
                val moduleRecord = it.fetchedModule.record()
                val moduleGroupRecord = it.fetchedModule.fetchedModuleGroup.record()
                val studySegmentRecord = it.fetchedModule.fetchedModuleGroup.fetchedStudySegment.record()

                val moduleCourseRecord = moduleCourseRecord(moduleRecord, courseRecord)
                val studySegmentModuleGroupRecord = studySegmentModuleGroupRecord(studySegmentRecord, moduleGroupRecord)
                val moduleGroupModuleRecord = moduleGroupModuleRecord(moduleGroupRecord, moduleRecord)
            }
            ctx.update(s)
                .set(s.FETCHED, DSL.currentLocalDateTime())
                .where(s.NUMBER.eq(studyNumber))
                .execute()
        } catch (e: Exception) {
            log.error("Error inserting courses", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : TableRecord<*>> T.insertReturning(): T {
        return ctx.insertInto(this.table)
            .set(this)
            .returning()
            .fetchOne() as T
    }

    fun fetchStudyModuleGroups(studyNumber: String, segmentId: Int): List<JModuleGroupRecord> {
        return ctx.select(mg.asterisk())
            .from(mg)
            .join(ssmg).on(ssmg.MODULE_GROUP_ID.eq(mg.ID))
            .where(mg.STUDY_NUMBER.eq(studyNumber))
            .and(ssmg.STUDY_SEGMENT_ID.eq(segmentId))
            .orderBy(mg.ID)
            .fetchInto()
    }

    fun fetchModules(moduleGroupId: Int): List<JModuleRecord> {
        return ctx.select(m.asterisk())
            .from(m)
            .join(mgm).on(mgm.MODULE_ID.eq(m.ID))
            .where(mgm.MODULE_GROUP_ID.eq(moduleGroupId))
            .orderBy(m.ID)
            .fetchInto()
    }

    fun fetchCourses(moduleId: Int): List<JCourseRecord> {
        return ctx.select(c.asterisk())
            .from(c)
            .join(mc).on(mc.COURSE_ID.eq(c.ID))
            .where(mc.MODULE_ID.eq(moduleId))
            .orderBy(c.NAME)
            .fetchInto()
    }
}
