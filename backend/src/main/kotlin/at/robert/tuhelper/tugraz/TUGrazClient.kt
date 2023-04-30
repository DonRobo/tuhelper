package at.robert.tuhelper.tugraz

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
    val type: TUGrazClientStudyType
)

private interface Node {
    val studyId: Int
    val nodeNr: Int?
    val nodeName: String?
}

private data class StudyComponent(
    val name: String,
    val ects: Int,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node

private data class StudyComponentChoice(
    val name: String,
    val sst: Int,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node

private data class StudyModule(
    val name: String,
    val ects: Int?,
    val sst: Int?,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node

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

            val number = columns[0].text()
            val name = columns[1].text()
            val type = TUGrazClientStudyType.fromText(columns[2].text())
            val ects = columns[4].text().toIntOrNull()

            TUGrazClientStudy(number, name, ects, type)
        }.also {
            val duplicates = it.groupBy { study -> study.number }.filterValues { studies -> studies.size > 1 }
            duplicates.forEach { (number, studies) ->
                log.warn("Duplicate study number '$number' found: ${studies.joinToString { it.name }}")
            }
        }.distinctBy { study -> study.number }
    }

    private suspend fun fetchNode(node: Node): Document {
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

    private data class ParsedNode(
        override val studyId: Int,
        override val nodeNr: Int?,
        override val nodeName: String?,
        val name: String,
        val semester: Int?,
        val ects: Int?,
        val sst: Int?,
    ) : Node

    private fun Document.parseNodes(studyId: Int): List<ParsedNode> {
        val studyComponents = mutableListOf<ParsedNode>()
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

            studyComponents.add(
                ParsedNode(
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

        return studyComponents
    }

    private suspend fun getStudyComponents(studiumId: Int): List<StudyComponent> {
        val response = httpClient.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbstpcs.showSpoTree")
            parameter("pSJNr", "1666")
            parameter("pStStudiumNr", "")
            parameter("pStartSemester", "")
            parameter("pStpStpNr", studiumId)
        }
        val doc = Jsoup.parse(response.body<String>())

        val parsedNodes = doc.parseNodes(studiumId).let {
            it.subList(1, it.size)
        }

        return parsedNodes.map {
            StudyComponent(
                name = it.name,
                ects = it.ects!!,
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

    suspend fun parseMaster() {
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

    suspend fun parseSubMaster() {
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

    private suspend fun getStudyComponentChoices(studyComponent: StudyComponent): List<StudyComponentChoice> {
        val doc = fetchNode(studyComponent)
        val parsedNodes = doc.parseNodes(studyComponent.studyId)

        return parsedNodes.map {
            StudyComponentChoice(
                name = it.name,
                nodeNr = it.nodeNr!!,
                nodeName = it.nodeName!!,
                studyId = it.studyId,
                sst = it.sst!!,
            )
        }
    }

    private suspend fun getStudyModules(studyComponentChoice: StudyComponentChoice): List<StudyModule> {
        val doc = fetchNode(studyComponentChoice)
        val parsedNodes = doc.parseNodes(studyComponentChoice.studyId)

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
//            val studyComponents = client.getStudyComponents(1022)
//            studyComponents.forEach {
//                println(it)
//            }
//            val studyComponent = studyComponents.first()
//            val studyComponentChoices: List<StudyComponentChoice> = client.getStudyComponentChoices(studyComponent)
//            studyComponentChoices.forEach {
//                println(it)
//            }
//            val studyComponentChoice = studyComponentChoices.first()
//            val studyModules = client.getStudyModules(studyComponentChoice)
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
