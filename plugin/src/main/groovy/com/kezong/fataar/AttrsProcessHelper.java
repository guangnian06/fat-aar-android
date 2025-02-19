package com.kezong.fataar;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;


import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * attrs资源处理辅助类
 */
public class AttrsProcessHelper {

    public static void hookResourceProcess(Project project, FatAarExtension fatAarExtension) {
        project.getPlugins().withId("com.android.library", plugin -> {
            AndroidComponentsExtension<?, ?, Variant> components =
                    project.getExtensions().getByType(AndroidComponentsExtension.class);
            components.onVariants(components.selector().all(), variant -> {
                String variantName = capitalize(variant.getName());
                String beforeTaskName = "generate" + variantName + "Resources";
                String afterTaskName = "package" + variantName + "Resources";
                String taskName = "reBundleAar" + variantName;
                TaskProvider resTask = project.getTasks().register(taskName, Task.class, task -> {
                    task.doFirst(task1 -> {
                        List<File> parentDirs = new ArrayList<>();
                        findResDirs(getExplodedAarFile(project), variant, parentDirs);
                    });
                });

                project.getTasks().withType(Task.class).configureEach(task -> {
                    if (task.getName().equals(afterTaskName)) {
                        task.dependsOn(resTask);
                    }
                    if (task.getName().equals(beforeTaskName)) {
                        task.finalizedBy(resTask);
                    }
                });
            });
        });

    }



    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static void findResDirs(File dir, Variant variant, List<File> parentDirs) {
        if (dir.isDirectory()) {
            // 获取目录下的所有文件和子目录
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    // 如果是目录，检查是否是 res 目录
                    if (file.isDirectory()) {
                        if (file.getName().equals(variant.getName())) {
                            // 添加 res 目录的父目录
                            parentDirs.add(file);
                        } else {
                            // 递归查找
                            findResDirs(file, variant, parentDirs);
                        }
                    }
                }
            }
        }
    }

    private static File getExplodedAarFile(Project project) {
        return project.file(project.getBuildDir() + "/intermediates/exploded-aar");
    }
}
