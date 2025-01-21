package com.kezong.fataar;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
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

                        boolean isNotFilter = fatAarExtension.excludeDeclareStyleAttrsFormatPath.isEmpty();
                        System.out.println("processValuesXmlRepeatAttr isNotFilter " + isNotFilter);
                        // 打印所有找到的 res 目录的父目录路径
                        for (File dir : parentDirs) {
                            if (isNotFilter) {
                                processValuesXmlRepeatAttr(dir, fatAarExtension);
                            } else {
                                for (String pkg : fatAarExtension.excludeDeclareStyleAttrsFormatPath) {
                                    String pkgFilePath = dir.getAbsolutePath();
                                    String pkgPath = pkg.replace(":", "/");
                                    if (pkgFilePath.contains(pkgPath)) {
                                        processValuesXmlRepeatAttr(dir, fatAarExtension);
                                    }
                                }
                            }

                        }
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

    // 移除多个aar内声明了同名attr导致的问题
    public static void processValuesXmlRepeatAttr(File pkgFile, FatAarExtension fatAarExtension) {
        final File valueXml = new File(pkgFile, "res/values/values.xml");
        if (!valueXml.exists()) {
            return;
        }
        // 创建SAXReader来读取XML文件
        SAXReader reader = new SAXReader();
        Document document;
        try {
            document = reader.read(valueXml);
            // 获取根元素
            Element root = document.getRootElement();

            // 查找<declare-styleable>元素
            List<Element> styleableElements = root.elements("declare-styleable");
            for (Element styleable : styleableElements) {
                String styleableName = styleable.attributeValue("name");
                if (fatAarExtension.excludeDeclareStyleAttrsFormat.containsKey(styleableName)) {
                    System.out.println("processValuesXmlRepeatAttr start remove " + styleable.asXML());
                    // 查找<attr>元素
                    List<Element> attrElements = styleable.elements("attr");
                    for (Element attr : attrElements) {
                        if (fatAarExtension.excludeDeclareStyleAttrsFormat.get(styleableName).equals(attr.attributeValue("name"))) {
                            // 移除format属性
                            attr.remove(attr.attribute("format"));
                        }
                    }
                    System.out.println("processValuesXmlRepeatAttr end remove " + styleable.asXML());
                }
            }

            // 将修改后的XML写回文件
            OutputFormat format = OutputFormat.createPrettyPrint();
            XMLWriter writer = new XMLWriter(new FileWriter(valueXml), format);
            writer.write(document);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            System.out.println("processValuesXmlRepeatAttr end Exception " + e.getMessage());
        }
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
