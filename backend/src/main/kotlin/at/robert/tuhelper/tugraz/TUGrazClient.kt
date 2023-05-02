package at.robert.tuhelper.tugraz

import at.robert.tuhelper.getUriParameter
import at.robert.tuhelper.log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component

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
    val ects: Int?,
    val type: TUGrazClientStudyType,
    val studyId: Int,
)

private interface TUGrazClientNode {
    val studyId: Int
    val nodeNr: Int?
    val nodeName: String?
}

data class TUGrazClientStudySegment(
    val name: String,
    val ects: Int?,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : TUGrazClientNode

private data class TUGrazClientStudySegmentChoice(
    val name: String,
    val sst: Int,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : TUGrazClientNode

private data class StudyModule(
    val name: String,
    val ects: Int?,
    val sst: Int?,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : TUGrazClientNode

private data class StudyCourse(
    val name: String,
    val ects: Int?,
    val sst: Int?,
    val semester: Int,
)

@Component
class TUGrazClient(
    private val httpClient: HttpClient,
) {

    suspend fun fetchStudies(): List<TUGrazClientStudy> {
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
            val ects = columns[4].text().toIntOrNull()
            val studyId = link.getUriParameter("pStpStpNr")!!.toInt()

            TUGrazClientStudy(number, name, ects, type, studyId)
        }.also {
            val duplicates = it.groupBy { study -> study.number }.filterValues { studies -> studies.size > 1 }
            duplicates.forEach { (number, studies) ->
                log.warn("Duplicate study number '$number' found: ${studies.joinToString { it.name }}")
            }
        }.distinctBy { study -> study.number }
    }

    private suspend fun fetchNode(node: TUGrazClientNode): Document {
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
        val ects: Int?,
        val sst: Int?,
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
                ?.toIntOrNull()
            val sst = row // tr
                .select("div.R")[1]
                ?.text()
                ?.toIntOrNull()

            studySegments.add(
                TUGrazClientParsedNode(
                    studyId = studyId,
                    nodeNr = nodeNr,
                    nodeName = nodeName,
                    name = name,
                    semester = semester,
                    ects = ects,
                    sst = sst,
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
                nodeNr = it.nodeNr!!,
                nodeName = it.nodeName!!,
                studyId = it.studyId,
            )
        }
    }

    private suspend fun request(params: Map<String, String>): Document {
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbstpcs.showSpoTree")
            params.forEach { (k, v) ->
                parameter(k, v)
            }
        }
        return Jsoup.parse(response.body<String>())
    }

    private suspend fun request2(params: Map<String, String>): Document {
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbStpCs.cbSpoTree/NC_5211")
            params.forEach { (k, v) ->
                parameter(k, v)
            }
        }
        return Jsoup.parse(response.body<String>())
    }

    private suspend fun parseMaster() {
        val doc = request(
            mapOf(
                "pSJNr" to "1666",
                "pStStudiumNr" to "",
                "pStartSemester" to "",
                "pStpStpNr" to "1022"
            )
        )

        doc.select("tr td a.KnotenLink").forEach { element ->
            println(element.text())
            println(element)
        }
    }

    private suspend fun parseSubMaster() {
        //?pStStudiumNr=&pStpStpNr=1022&pStPersonNr=&pSJNr=1666&pIsStudSicht=FALSE&pShowErg=J&pHideInactive=TRUE&pCaller=&pStpKnotenNr=101258&pId=kn101258&pAction=0&pStpSTypNr=&pStartSemester=
        val doc = request2(
            mapOf(
                "pStStudiumNr" to "",
                "pStpStpNr" to "1022",
                "pStPersonNr" to "",
                "pSJNr" to "1666",
                "pIsStudSicht" to "FALSE",
                "pShowErg" to "J",
                "pHideInactive" to "TRUE",
                "pCaller" to "",
                "pStpKnotenNr" to "101258",
                "pId" to "kn101258",
                "pAction" to "0",
                "pStpSTypNr" to "",
                "pStartSemester" to ""
            )
        )
        println(doc)
    }

    private suspend fun getStudySegmentChoices(studySegment: TUGrazClientStudySegment): List<TUGrazClientStudySegmentChoice> {
        val doc = fetchNode(studySegment)
        val parsedNodes = doc.parseNodes(studySegment.studyId)

        return parsedNodes.map {
            TUGrazClientStudySegmentChoice(
                name = it.name,
                nodeNr = it.nodeNr!!,
                nodeName = it.nodeName!!,
                studyId = it.studyId,
                sst = it.sst!!,
            )
        }
    }

    private suspend fun getStudyModules(studySegmentChoice: TUGrazClientStudySegmentChoice): List<StudyModule> {
        val doc = fetchNode(studySegmentChoice)
        val parsedNodes = doc.parseNodes(studySegmentChoice.studyId)

        return parsedNodes.map {
            StudyModule(
                name = it.name,
                nodeNr = it.nodeNr!!,
                nodeName = it.nodeName!!,
                studyId = it.studyId,
                ects = it.ects,
                sst = it.sst,
            )
        }
    }

    private suspend fun getCourses(studyModule: StudyModule): List<StudyCourse> {
        val doc = fetchNode(studyModule)
        val parsedNodes = doc.parseNodes(studyModule.studyId)

        return parsedNodes.map {
            StudyCourse(
                name = it.name,
                ects = it.ects,
                sst = it.sst,
                semester = it.semester!!,
            )
        }
    }

    companion object {

        suspend fun runTests() {
            val client = TUGrazClient(HttpClient(CIO) {})
//    client.parseMaster()
//    client.parseSubMaster()
//            val studySegments = client.getStudySegments(1022)
//            studySegments.forEach {
//                println(it)
//            }
//            val studySegment = studySegments.first()
//            val studySegmentChoices: List<StudySegmentChoice> = client.getStudySegmentChoices(studySegment)
//            studySegmentChoices.forEach {
//                println(it)
//            }
//            val studySegmentChoice = studySegmentChoices.first()
//            val studyModules = client.getStudyModules(studySegmentChoice)
//            studyModules.forEach {
//                println(it)
//            }
//            val studyModule = studyModules.first()
//            val courses = client.getCourses(studyModule)
//            courses.forEach {
//                println(it)
//            }
            client.fetchStudies().filter { it.type == TUGrazClientStudyType.MASTER }.forEach { fetchStudy ->
                println(fetchStudy)
            }
        }

    }

}

suspend fun main() {
    TUGrazClient.runTests()
}
