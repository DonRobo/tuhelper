package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyModuleGroup

class SegmentConfig {
    var requiredEcts: Int? = null

    val moduleGroups = mutableListOf<Pair<ModuleGroupSelector, ModuleGroupConfig>>()

    fun moduleGroups(moduleGroupLetters: Pair<Char, Char>, block: ModuleGroupConfig.() -> Unit) {
        val letters = moduleGroupLetters.first..moduleGroupLetters.second
        moduleGroups(*letters.map { it.toString() }.toTypedArray(), block = block)
    }

    fun moduleGroups(vararg configStrings: String, block: ModuleGroupConfig.() -> Unit) {
        configStrings.forEach {
            moduleGroup(it, block)
        }
    }

    fun moduleGroup(configString: String, block: ModuleGroupConfig.() -> Unit) {
        val config = ModuleGroupConfig()
        config.block()
        moduleGroups.add(ModuleGroupSelector.byConfigString(configString) to config)
    }

    fun addModuleGroup(moduleGroupName: String, block: ModuleGroupConfig.() -> Unit) {
        val config = ModuleGroupConfig()
        config.block()
        moduleGroups.add(
            ModuleGroupSelector.newModuleGroup(
                StudyModuleGroup(
                    -1,
                    moduleGroupName,
                    emptyList()
                )
            ) to config
        )
    }
}