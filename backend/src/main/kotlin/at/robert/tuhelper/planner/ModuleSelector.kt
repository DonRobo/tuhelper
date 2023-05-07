package at.robert.tuhelper.planner

import at.robert.tuhelper.data.StudyModule

interface ModuleSelector : Selector<StudyModule> {

    fun chooseModule(moduleList: List<StudyModule>): StudyModule?

    override fun choose(from: List<StudyModule>): List<StudyModule> {
        return listOfNotNull(chooseModule(from))
    }

    companion object {
        fun byConfigString(configString: String, optional: Boolean = false): ModuleSelector {
            return when {
                configString.length == 1 -> moduleByLetter(configString, optional)
                configString.matches(Regex("[A-Z]\\d]")) -> moduleByLetter(configString, optional)
                else -> nameContains(configString)
            }
        }

        fun moduleByLetter(letter: String, optional: Boolean = false): ModuleSelector {
            require(letter.all { it.isUpperCase() || it.isDigit() })

            val regex = Regex("\\[.+/.+/.+$letter] $letter: .+")
            return object : ModuleSelector {
                override fun chooseModule(moduleList: List<StudyModule>): StudyModule? {
                    return moduleList.filter { regex.matches(it.name) }.let {
                        when {
                            it.size == 1 -> it.single()
                            it.isEmpty() && optional -> null
                            else -> error("Error selecting module with letter `$letter`: ${moduleList.map { it.name }}")
                        }
                    }
                }
            }
        }

        fun nameContains(moduleName: String, optional: Boolean = false): ModuleSelector {
            return object : ModuleSelector {
                override fun chooseModule(moduleList: List<StudyModule>): StudyModule? {
                    return moduleList.filter { it.name.contains(moduleName) }.let {
                        when {
                            it.size == 1 -> it.single()
                            it.isEmpty() && optional -> null
                            else -> error("Error selecting module name containing `$moduleName`: ${moduleList.map { it.name }}")
                        }
                    }
                }
            }
        }

        fun newModule(studyModule: StudyModule): ModuleSelector {
            return object : ModuleSelector {
                override fun chooseModule(moduleList: List<StudyModule>): StudyModule {
                    return studyModule
                }
            }
        }
    }
}
