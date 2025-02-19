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

    private static class AttrDefinition {
        Set<String> formats = new HashSet<>()
        Element enumDefinition
        int occurrences = 0
    }

    void processValuesXml(File valuesXml) {
        if (!valuesXml.exists()) {
            return
        }

        SAXReader reader = new SAXReader()
        Document document = reader.read(valuesXml)
        Element root = document.getRootElement()

        // Track all attr definitions and their formats
        Map<String, AttrDefinition> attrDefinitions = new HashMap<>()

        // First collect existing root attrs
        root.elements("attr").each { attr ->
            String attrName = attr.attributeValue("name")
            AttrDefinition def = new AttrDefinition()
            String format = attr.attributeValue("format")
            if (format) {
                format.split("\\|").each { f -> def.formats.add(f.trim()) }
            }
            if (attr.elements("enum").size() > 0) {
                def.enumDefinition = attr
            }
            attrDefinitions.put(attrName, def)
        }

        // Collect attrs from declare-styleable and count occurrences
        List<Element> styleableElements = root.elements("declare-styleable")
        styleableElements.each { styleable ->
            styleable.elements("attr").each { attr ->
                String attrName = attr.attributeValue("name")
                AttrDefinition def = attrDefinitions.computeIfAbsent(attrName, { k -> new AttrDefinition() })
                def.occurrences++
                
                String format = attr.attributeValue("format")
                if (format) {
                    format.split("\\|").each { f -> def.formats.add(f.trim()) }
                }
                if (attr.elements("enum").size() > 0 && !def.enumDefinition) {
                    def.enumDefinition = attr
                }
            }
        }

        // Move only duplicate attrs to root level with merged formats
        attrDefinitions.each { attrName, def ->
            if (def.occurrences > 1) {
                // Create or update root attr
                Element rootAttr
                List<Element> existingAttrs = root.elements("attr").findAll { it.attributeValue("name") == attrName }
                if (existingAttrs.size() > 0) {
                    rootAttr = existingAttrs.first()
                    // Merge formats with existing
                    String existingFormat = rootAttr.attributeValue("format")
                    if (existingFormat) {
                        existingFormat.split("\\|").each { f -> def.formats.add(f.trim()) }
                    }
                } else {
                    rootAttr = def.enumDefinition ? def.enumDefinition.createCopy() : root.addElement("attr")
                    rootAttr.addAttribute("name", attrName)
                }

                // Set merged format if any
                if (!def.formats.isEmpty()) {
                    rootAttr.addAttribute("format", def.formats.join("|"))
                }

                // Update declare-styleable references
                styleableElements.each { styleable ->
                    styleable.elements("attr").findAll { it.attributeValue("name") == attrName }.each { attr ->
                        // Remove format and enum definitions, keeping only the name reference
                        attr.clearContent()
                        attr.attributes().removeIf { it.name != "name" }
                    }
                }
            }
        }

        // Write back the modified XML
        OutputFormat format = OutputFormat.createPrettyPrint()
        XMLWriter writer = new XMLWriter(new FileWriter(valuesXml), format)
        writer.write(document)
        writer.close()
    }
}
