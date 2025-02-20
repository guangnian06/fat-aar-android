package com.kezong.fataar

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import com.android.build.gradle.api.LibraryVariant

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
                File mergedDir = new File(mProject.buildDir, "intermediates/incremental/${mVariant.name}/merged.dir/values")
                if (mergedDir.exists()) {
                    File valuesXml = new File(mergedDir, "values.xml")
                    if (valuesXml.exists()) {
                        ResourceModifier modifier = new ResourceModifier(mProject, mVariant)
                        modifier.processValuesXml(valuesXml)
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
