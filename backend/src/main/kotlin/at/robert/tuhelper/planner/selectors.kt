package at.robert.tuhelper.planner

enum class DefaultHandling {
    ADD, IGNORE, FAIL
}

interface Selector<T> {
    fun choose(from: List<T>): List<T>
}

interface Config

interface SelectorBasedConfig<StudyT, SubConfigT : Config> : Config {

    val handleDefaults: DefaultHandling
    val subConfigs: List<Pair<Selector<StudyT>, SubConfigT>>

    fun createSubConfig(obj: StudyT): SubConfigT

}

fun <StudyT, SolverT, ConfigT : Config> SelectorBasedConfig<StudyT, ConfigT>.configureSubs(
    list: List<StudyT>,
    block: (ConfigT, StudyT) -> SolverT?
): List<SolverT> {
    val objectsToChooseFrom = list.toMutableList()
    val confs = subConfigs.toMutableList()
    val results = mutableListOf<SolverT?>()

    while (confs.isNotEmpty()) {
        val (selector, config) = confs.removeFirst()
        val chosen = selector.choose(objectsToChooseFrom)
        objectsToChooseFrom.removeAll(chosen)
        chosen.forEach {
            results.add(block(config, it))
        }
    }
    when (handleDefaults) {
        DefaultHandling.IGNORE -> {
            // do nothing
        }

        DefaultHandling.FAIL -> require(objectsToChooseFrom.isEmpty()) {
            "Couldn't configure ${this::class.simpleName}: These weren't configured: $objectsToChooseFrom"
        }

        DefaultHandling.ADD -> results.addAll(objectsToChooseFrom.map {
            block(createSubConfig(it), it)
        })
    }

    return results.filterNotNull()
}
