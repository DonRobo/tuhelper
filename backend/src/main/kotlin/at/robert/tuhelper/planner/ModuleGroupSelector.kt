package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyModuleGroup

interface ModuleGroupSelector {
    fun chooseModuleGroup(moduleGroupList: List<StudyModuleGroup>): StudyModuleGroup

    companion object {
        fun byConfigString(configString: String): ModuleGroupSelector {
            return when {
                configString.length == 1 -> moduleGroupByLetter(configString.single())
                else -> nameContains(configString)
            }
        }

        fun moduleGroupByLetter(letter: Char): ModuleGroupSelector {
            require(letter.isUpperCase())

            val regex = Regex("\\[.+\\/.+\\/.+$letter] $letter: .+")
            return object : ModuleGroupSelector {
                override fun chooseModuleGroup(moduleGroupList: List<StudyModuleGroup>): StudyModuleGroup {
                    return moduleGroupList.singleOrNull { regex.matches(it.name) }
                        ?: error("No module group with name containing letter `$letter` found: ${moduleGroupList.map { it.name }}")
                }
            }
        }

        fun nameContains(string: String): ModuleGroupSelector {
            return object : ModuleGroupSelector {
                override fun chooseModuleGroup(moduleGroupList: List<StudyModuleGroup>): StudyModuleGroup {
                    return moduleGroupList.singleOrNull { it.name.contains(string) }
                        ?: error("No module group with name containing `$string` found: ${moduleGroupList.map { it.name }}")
                }
            }
        }

        fun newModuleGroup(studyModuleGroup: StudyModuleGroup): ModuleGroupSelector {
            return object : ModuleGroupSelector {
                override fun chooseModuleGroup(moduleGroupList: List<StudyModuleGroup>): StudyModuleGroup {
                    return studyModuleGroup
                }
            }
        }
    }
}
