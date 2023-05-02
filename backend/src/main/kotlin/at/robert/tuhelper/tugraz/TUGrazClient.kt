package at.robert.tuhelper.tugraz

import at.robert.tuhelper.getUriParameter
import at.robert.tuhelper.log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.math.BigDecimal

enum class TUGrazClientStudyType(
    val text: String
) {
    BACHELOR("Bachelorstudium"),
    INDIVIDUAL_BACHELOR("Individuelles Bachelorstudium"),
    INDIVIDUAL_MASTER("Individuelles Masterstudium"),
    MASTER("Masterstudium"),
    DOCTORATE("Doktoratsstudium"),
    UNIVERSITY_COURSE("Universitätslehrgang"),
    OTHER("sonstiges Studium"),
    TEACHING_EXTENSION("Erweiterungsstudium Lehramt"),
    TEACHING_BACHELOR("Lehramt Bachelor"),
    TEACHING_MASTER("Lehramt Master");

    companion object {
        fun fromText(text: String): TUGrazClientStudyType {
            return values().singleOrNull { it.text == text }
                ?: throw IllegalArgumentException("No TUGrazClientStudyType found for text '$text'")
        }
    }
}

data class TUGrazClientStudy(
    val number: String,
    val name: String,
    val ects: BigDecimal?,
    val type: TUGrazClientStudyType,
    val studyId: Int,
)

interface TUGrazClientNode {
    val studyId: Int
    val nodeNr: Int?
    val nodeName: String?
}

data class TUGrazClientStudySegment(
    val name: String,
    val ects: BigDecimal?,
    override val nodeNr: Int?,
    override val nodeName: String?,
    override val studyId: Int,
) : TUGrazClientNode

data class TUGrazClientStudySegmentModuleGroup(
    val name: String,
    override val nodeNr: Int?,
    override val nodeName: String?,
    override val studyId: Int,
) : TUGrazClientNode

data class TUGrazClientStudyModule(
    val name: String,
    val ects: BigDecimal?,
    override val nodeNr: Int?,
    override val nodeName: String?,
    override val studyId: Int,
) : TUGrazClientNode

data class TUGrazClientStudyCourse(
    val name: String,
    val ects: BigDecimal?,
    val semester: Int?,
)

@Component
class TUGrazClient(
    private val httpClient: HttpClient,
) {

    suspend fun fetchStudies(): List<TUGrazClientStudy> {
        log.debug("Fetching studies")
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbStpPortfolio.wbStpList")
        }
        val doc = Jsoup.parse(response.body<String>())

        return doc.select("tr.coRow.hi.coTableR.invisible").map { study ->
            val columns = study.select("td").also { require(it.size == 8) }
            val link = columns[1].select("a").first()!!.attr("href")

            val number = columns[0].text()
            val name = columns[1].text()
            val type = TUGrazClientStudyType.fromText(columns[2].text())
            val ects = columns[4].text().replace(",", ".").toBigDecimalOrNull()
            val studyId = link.getUriParameter("pStpStpNr")!!.toInt()

            TUGrazClientStudy(number, name, ects, type, studyId)
        }.also {
            val duplicates = it.groupBy { study -> study.number }.filterValues { studies -> studies.size > 1 }
            duplicates.forEach { (number, studies) ->
                log.warn("Duplicate study number '$number' found: ${studies.joinToString { it.name }}")
            }
        }.distinctBy { study -> study.number }.also {
            log.debug("Fetched ${it.size} studies")
        }
    }

    private suspend fun fetchNode(node: TUGrazClientNode): Document? {
        if (node.nodeNr == null || node.nodeName == null) {
            return null
        }
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbStpCs.cbSpoTree/NC_5211")
            parameter("pStStudiumNr", "")
            parameter("pStpStpNr", node.studyId)
            parameter("pStPersonNr", "")
            parameter("pSJNr", "1666")
            parameter("pIsStudSicht", "FALSE")
            parameter("pShowErg", "J")
            parameter("pHideInactive", "TRUE")
            parameter("pCaller", "")
            parameter("pStpKnotenNr", node.nodeNr)
            parameter("pId", node.nodeName)
            parameter("pAction", "0")
            parameter("pStpSTypNr", "")
            parameter("pStartSemester", "")
        }

        return Jsoup.parse(response.body<String>()).let {
            it.select("instruction[action=\"insertAfterElement\"]").single().text().let {
                Jsoup.parse(it)
            }
        }
    }

    private data class TUGrazClientParsedNode(
        override val studyId: Int,
        override val nodeNr: Int?,
        override val nodeName: String?,
        val name: String,
        val semester: Int?,
        val ects: BigDecimal?,
    ) : TUGrazClientNode

    private fun Document.parseNodes(studyId: Int): List<TUGrazClientParsedNode> {
        val studySegments = mutableListOf<TUGrazClientParsedNode>()
        select("a.KnotenLink").forEach {
            val row = it.parent()!!// span
                .parent()!! // div
                .parent()!! // td
                .parent()!!

            val linkRegex = Regex("cs_loadSubElems\\.call\\(this,'(.*?)',null,'(.*?)',null,null\\)")
            val match = linkRegex.find(it.attr("onclick"))

            val nodeName: String?
            val nodeNr: Int?

            if (match == null) {
                nodeName = null
                nodeNr = null
            } else {
                nodeName = match.groupValues[1]
                nodeNr = match.groupValues[2].toInt()
            }

            val name = it.text()

            val semester = row // tr
                .select("div.C").single()
                ?.text()?.filter { c -> c.isDigit() }
                ?.toIntOrNull()
            val ects = row // tr
                .select("div.R")[0]
                ?.text()
                ?.replace(",", ".")
                ?.toBigDecimalOrNull()

            studySegments.add(
                TUGrazClientParsedNode(
                    studyId = studyId,
                    nodeNr = nodeNr,
                    nodeName = nodeName,
                    name = name,
                    semester = semester,
                    ects = ects,
                )
            )
        }

        return studySegments
    }

    suspend fun getStudySegments(studyId: Int): List<TUGrazClientStudySegment> {
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbstpcs.showSpoTree")
            parameter("pSJNr", "1666") //TODO find out what this means
            parameter("pStStudiumNr", "")
            parameter("pStartSemester", "")
            parameter("pStpStpNr", studyId)
        }
        val doc = Jsoup.parse(response.body<String>())

        val parsedNodes = doc.parseNodes(studyId).let {
            it.subList(1, it.size)
        }

        return parsedNodes.map {
            TUGrazClientStudySegment(
                name = it.name,
                ects = it.ects,
                nodeNr = it.nodeNr,
                nodeName = it.nodeName,
                studyId = it.studyId,
            )
        }
    }

    suspend fun getStudySegmentModuleGroups(studySegment: TUGrazClientStudySegment): List<TUGrazClientStudySegmentModuleGroup> {
        log.debug("Fetching module groups for study segment ${studySegment.name}")
        val doc = fetchNode(studySegment)
        val parsedNodes = doc?.parseNodes(studySegment.studyId) ?: emptyList<TUGrazClientParsedNode>().also {
            log.warn("Failed to fetch module groups for study segment ${studySegment.name}")
        }

        return parsedNodes.map {
            TUGrazClientStudySegmentModuleGroup(
                name = it.name,
                nodeNr = it.nodeNr,
                nodeName = it.nodeName,
                studyId = it.studyId,
            )
        }.also {
            log.debug("Fetched ${it.size} module groups for study segment ${studySegment.name}")
        }
    }

    suspend fun getStudyModules(studySegmentModuleGroup: TUGrazClientStudySegmentModuleGroup): List<TUGrazClientStudyModule> {
        log.debug("Fetching modules for module group ${studySegmentModuleGroup.name}")
        val doc = fetchNode(studySegmentModuleGroup)
        val parsedNodes = doc?.parseNodes(studySegmentModuleGroup.studyId) ?: emptyList<TUGrazClientParsedNode>().also {
            log.warn("Failed to fetch modules for module group ${studySegmentModuleGroup.name}")
        }

        return parsedNodes.map {
            TUGrazClientStudyModule(
                name = it.name,
                nodeNr = it.nodeNr,
                nodeName = it.nodeName,
                studyId = it.studyId,
                ects = it.ects,
            )
        }.also {
            log.debug("Fetched ${it.size} modules for module group ${studySegmentModuleGroup.name}")
        }
    }

    suspend fun getCourses(studyModule: TUGrazClientStudyModule): List<TUGrazClientStudyCourse> {
        log.debug("Fetching courses for module ${studyModule.name}")
        val doc = fetchNode(studyModule)
        val parsedNodes = doc?.parseNodes(studyModule.studyId) ?: emptyList<TUGrazClientParsedNode>().also {
            log.warn("Failed to fetch courses for module ${studyModule.name}")
        }

        return parsedNodes.map {
            TUGrazClientStudyCourse(
                name = it.name,
                ects = it.ects,
                semester = it.semester,
            )
        }.also {
            log.debug("Fetched ${it.size} courses for module ${studyModule.name}")
        }
    }
}
