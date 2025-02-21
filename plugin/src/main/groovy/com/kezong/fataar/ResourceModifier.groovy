package com.kezong.fataar

import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.Project
import com.android.build.gradle.api.LibraryVariant

class ResourceModifier {

    static void processValuesXml(File valuesXml) {
        if (!valuesXml.exists()) {
            return
        }

        SAXReader reader = new SAXReader()
        Document document = reader.read(valuesXml)
        Element root = document.getRootElement()

        // Track all attr definitions and their formats
        Map<String, List<Element>> attrDefinitions = new HashMap<>()

        // Process root attrs first
        root.elements("attr").each { attr ->
            String attrName = attr.attributeValue("name")
            attrDefinitions.computeIfAbsent(attrName, { [] }).add(attr)
        }

        // Process declare-styleable attrs in single pass
        root.elements("declare-styleable").each { styleable ->
            styleable.elements("attr").each { attr ->
                // Skip reference-only attrs
                if (attr.attributes().size() > 1 || attr.elements().size() > 0) {
                    attrDefinitions.computeIfAbsent(attr.attributeValue("name"), { [] }).add(attr)
                }
            }
        }

        // Move duplicate attrs to root level if they're all in declare-styleable
        attrDefinitions.each { attrName, attrList ->
            if (attrList.size() <= 1) {
                return
            }
            Map<String, String> enums = new HashMap<>()
            Map<String, String> flags = new HashMap<>()
            Set<String> formats = new HashSet<>()

            // Get first definition
            Element firstDef = attrList.first
            if (firstDef == null) {
                return
            }

            Element rootAttr = firstDef.parent == root ? firstDef : firstDef.createCopy()

            if (rootAttr !== firstDef) {
                root.add(rootAttr)
            }

            for (attr in attrList) {
                def format = attr.attribute("format")
                if (format) {
                    formats.addAll(format.value.split("\\|"))
                }
                for (e in attr.elements()) {
                    if (e.name == "enum") {
                        def existing = enums.put(e.attributeValue("name"), e.attributeValue("value"))
                        if (existing != null && existing != e.attributeValue("value")) {
                            throw new RuntimeException(
                                    "Attribute '${attrName}' has conflicting definitions:\n" +
                                            "enum '${e.attributeValue('name')}' has different values '${e.attributeValue('value')}' and '${existing}'")
                        }
                    } else if (e.name == "flag") {
                        def existing = flags.put(e.attributeValue("name"), e.attributeValue("value"))
                        if (existing != null && existing != e.attributeValue("value")) {
                            throw new RuntimeException(
                                    "Attribute '${attrName}' has conflicting definitions:\n" +
                                            "flag '${e.attributeValue('name')}' has different values '${e.attributeValue('value')}' and '${existing}'")
                        }
                    }
                }

                if (enums.size() > 0 && flags.size() > 0) {
                    throw new RuntimeException(
                            "Attribute '${attrName}' has conflicting definitions:\n" +
                                    "Cannot mix enum and flag definitions")
                }

                // Update format attribute with merged formats if any exist
                String existingFormat = rootAttr.attributeValue("format")
                if (!formats.isEmpty()) {
                    // Remove existing format if any
                    if (existingFormat != null) {
                        rootAttr.remove(rootAttr.attribute("format"))
                    }
                    rootAttr.addAttribute("format", formats.join("|"))
                }
                rootAttr.clearContent()
                for (entry in enums.entrySet()) {
                    Element enumElement = rootAttr.addElement("enum")
                    enumElement.addAttribute("name", entry.key)
                    enumElement.addAttribute("value", entry.value)
                }
                for (entry in flags.entrySet()) {
                    Element flagElement = rootAttr.addElement("flag")
                    flagElement.addAttribute("name", entry.key)
                    flagElement.addAttribute("value", entry.value)
                }
            }
            attrList.each { attr ->
                if (attr !== rootAttr) {
                    attr.clearContent()
                    attr.attributes().removeIf { it.name != "name" }
                }
            }
            FatUtils.logInfo("Merged formats for attribute '${attrName}': ${formats}")
        }
        // Write back the modified XML
        OutputFormat format = OutputFormat.createPrettyPrint()
        XMLWriter writer = new XMLWriter(new FileWriter(valuesXml), format)
        writer.write(document)
        writer.close()
    }
}
