package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class ResourceProcessor {
    private final Project mProject
    private final LibraryVariant mVariant

    ResourceProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
    }

    void processResources() {
        String packageTaskName = "package${mVariant.name.capitalize()}Resources"
        String taskPath = "process${mVariant.name.capitalize()}Resources"
        TaskProvider resourceTask = mProject.tasks.register(taskPath) {
            it.doLast {
                File packagedResDir = new File(mProject.buildDir, "intermediates/packaged_res/${mVariant.name}/$packageTaskName")
                FatUtils.logInfo("Processing resources in dir: ${packagedResDir} ${packagedResDir.exists()}")
                if (packagedResDir.exists()) {
                    for (File resDir : packagedResDir.listFiles()) {
                        if (resDir.isDirectory() && resDir.name.startsWith("values")) {
                            for (File valuesXml : resDir.listFiles()) {
                                if (valuesXml.exists()) {
                                    FatUtils.logInfo("Processing resources xml file: ${valuesXml}")
                                    ResourceModifier.processValuesXml(valuesXml)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hook into resource generation task
        TaskProvider resourcePackageTask = mProject.tasks.named(packageTaskName)
        if (resourcePackageTask != null) {
            resourcePackageTask.configure {
                finalizedBy(resourceTask)
            }
        }
    }
}
