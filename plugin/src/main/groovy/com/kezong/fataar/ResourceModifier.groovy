package com.kezong.fataar

import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.Project
import com.android.build.gradle.api.LibraryVariant

class ResourceModifier {
    private final Project mProject
    private final LibraryVariant mVariant

    ResourceModifier(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
    }

    void processValuesXml(File valuesXml) {
        if (!valuesXml.exists()) {
            return
        }

        SAXReader reader = new SAXReader()
        Document document = reader.read(valuesXml)
        Element root = document.getRootElement()

        // Collect all attr definitions
        Map<String, Element> attrDefinitions = new HashMap<>()
        List<Element> styleableElements = root.elements("declare-styleable")
        
        // First pass: collect all attr definitions
        styleableElements.each { styleable ->
            styleable.elements("attr").each { attr ->
                String attrName = attr.attributeValue("name")
                if (!attrDefinitions.containsKey(attrName)) {
                    attrDefinitions.put(attrName, attr.createCopy())
                }
            }
        }

        // Second pass: move attrs to root and update references
        attrDefinitions.values().each { attr ->
            root.add(attr)
        }

        styleableElements.each { styleable ->
            styleable.elements("attr").each { attr ->
                String attrName = attr.attributeValue("name")
                // Remove format attribute and other child elements
                attr.clearContent()
                attr.attributes().removeIf { it.name != "name" }
            }
        }

        // Write back the modified XML
        OutputFormat format = OutputFormat.createPrettyPrint()
        XMLWriter writer = new XMLWriter(new FileWriter(valuesXml), format)
        writer.write(document)
        writer.close()
    }
}
