package at.robert.tuhelper.tugraz

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class Study(
    val id: Int,
    val name: String,
    val ects: Int,
)

interface Node {
    val studyId: Int
    val nodeNr: Int?
    val nodeName: String?
}

data class StudyComponent(
    val name: String,
    val ects: Int,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node {
}

data class StudyComponentChoice(
    val name: String,
    val sst: Int,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node

data class StudyModule(
    val name: String,
    val ects: Int?,
    val sst: Int?,
    override val nodeNr: Int,
    override val nodeName: String,
    override val studyId: Int,
) : Node

data class StudyCourse(
    val name: String,
    val ects: Int?,
    val sst: Int?,
    val semester: Int,
)

class TUGrazClient {

    private val client = HttpClient(CIO) {
    }

    private suspend fun fetchNode(node: Node): Document {
        val response = client.get {
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

    suspend fun getStudyComponents(studiumId: Int): List<StudyComponent> {
        val response = client.get {
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
        val response = client.get {
            url("https://online.tugraz.at/tug_online/pl/ui/\$ctx;design=ca2;header=max;lang=de/wbstpcs.showSpoTree")
            params.forEach { (k, v) ->
                parameter(k, v)
            }
        }
        return Jsoup.parse(response.body<String>())
    }

    private suspend fun request2(params: Map<String, String>): Document {
        val response = client.get {
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

    suspend fun getStudyComponentChoices(studyComponent: StudyComponent): List<StudyComponentChoice> {
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

    suspend fun getStudyModules(studyComponentChoice: StudyComponentChoice): List<StudyModule> {
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

    suspend fun getCourses(studyModule: StudyModule): List<StudyCourse> {
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

}

suspend fun main() {
    val client = TUGrazClient()
//    client.parseMaster()
//    client.parseSubMaster()
    val studyComponents = client.getStudyComponents(1022)
    studyComponents.forEach {
        println(it)
    }
    val studyComponent = studyComponents.first()
    val studyComponentChoices: List<StudyComponentChoice> = client.getStudyComponentChoices(studyComponent)
    studyComponentChoices.forEach {
        println(it)
    }
    val studyComponentChoice = studyComponentChoices.first()
    val studyModules = client.getStudyModules(studyComponentChoice)
    studyModules.forEach {
        println(it)
    }
    val studyModule = studyModules.first()
    val courses = client.getCourses(studyModule)
    courses.forEach {
        println(it)
    }
}
