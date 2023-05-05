package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyModule
import at.robert.tuhelper.planner.ModuleSelector.Companion.byConfigString
import at.robert.tuhelper.planner.ModuleSelector.Companion.newModule

class ModuleGroupConfig {

    val modules = mutableListOf<Pair<ModuleSelector, ModuleConfig>>()

    fun module(configString: String, block: ModuleConfig.() -> Unit) {
        val config = ModuleConfig()
        config.block()
        modules.add(byConfigString(configString) to config)
    }

    fun excludeModule(configString: String) {
        val config = ModuleConfig()
        config.excluded()
        modules.add(byConfigString(configString, optional = true) to config)
    }

    fun addModule(moduleName: String, block: ModuleConfig.() -> Unit) {
        val config = ModuleConfig()
        config.block()
        modules.add(
            newModule(
                StudyModule(
                    id = -1,
                    name = moduleName,
                    courses = emptyList()
                )
            ) to config
        )
    }
}
