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

    private static class EnumFlagValidator {
        static class ValidationResult {
            boolean isValid
            String error

            ValidationResult(boolean isValid, String error = null) {
                this.isValid = isValid
                this.error = error
            }
        }

        static ValidationResult validate(Element attr1, Element attr2) {
            def enums1 = attr1.elements("enum")
            def flags1 = attr1.elements("flag")
            def enums2 = attr2.elements("enum")
            def flags2 = attr2.elements("flag")

            // Check for format mixing with enum/flag
            String format1 = attr1.attributeValue("format")
            String format2 = attr2.attributeValue("format")
            if ((enums1.size() > 0 || flags1.size() > 0) && format1) {
                return new ValidationResult(false,
                    "Attribute '${attr1.attributeValue('name')}' cannot mix enum/flag with other formats:\n" +
                    "Definition: ${attr1.asXML()}")
            }
            if ((enums2.size() > 0 || flags2.size() > 0) && format2) {
                return new ValidationResult(false,
                    "Attribute '${attr1.attributeValue('name')}' cannot mix enum/flag with other formats:\n" +
                    "Definition: ${attr2.asXML()}")
            }

            // Check for enum vs flag mismatch
            if ((enums1.size() > 0 && flags2.size() > 0) || (flags1.size() > 0 && enums2.size() > 0)) {
                return new ValidationResult(false, 
                    "Attribute '${attr1.attributeValue('name')}' has conflicting definitions:\n" +
                    "First definition: ${attr1.asXML()}\n" +
                    "Second definition: ${attr2.asXML()}\n" +
                    "Cannot mix enum and flag definitions")
            }

            // Compare enum/flag elements
            if (enums1.size() > 0 && enums2.size() > 0) {
                if (enums1.size() != enums2.size()) {
                    return new ValidationResult(false,
                        "Attribute '${attr1.attributeValue('name')}' has different number of enum elements:\n" +
                        "First definition: ${attr1.asXML()}\n" +
                        "Second definition: ${attr2.asXML()}")
                }
                
                def enumMap1 = enums1.collectEntries { [(it.attributeValue("name")): it.attributeValue("value")] }
                def enumMap2 = enums2.collectEntries { [(it.attributeValue("name")): it.attributeValue("value")] }
                if (enumMap1 != enumMap2) {
                    return new ValidationResult(false,
                        "Attribute '${attr1.attributeValue('name')}' has different enum definitions:\n" +
                        "First definition: ${attr1.asXML()}\n" +
                        "Second definition: ${attr2.asXML()}")
                }
            }

            if (flags1.size() > 0 && flags2.size() > 0) {
                if (flags1.size() != flags2.size()) {
                    return new ValidationResult(false,
                        "Attribute '${attr1.attributeValue('name')}' has different number of flag elements:\n" +
                        "First definition: ${attr1.asXML()}\n" +
                        "Second definition: ${attr2.asXML()}")
                }
                
                def flagMap1 = flags1.collectEntries { [(it.attributeValue("name")): it.attributeValue("value")] }
                def flagMap2 = flags2.collectEntries { [(it.attributeValue("name")): it.attributeValue("value")] }
                if (flagMap1 != flagMap2) {
                    return new ValidationResult(false,
                        "Attribute '${attr1.attributeValue('name')}' has different flag definitions:\n" +
                        "First definition: ${attr1.asXML()}\n" +
                        "Second definition: ${attr2.asXML()}")
                }
            }

            return new ValidationResult(true)
        }
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
            if (attr.elements("enum").size() > 0 || attr.elements("flag").size() > 0) {
                if (!def.formats.isEmpty()) {
                    throw new RuntimeException(
                        "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                        "Definition: ${attr.asXML()}")
                }
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
                    if (def.enumDefinition) {
                        throw new RuntimeException(
                            "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                            "Definition with enum/flag: ${def.enumDefinition.asXML()}\n" +
                            "Attempted to merge with format: ${format}")
                    }
                    format.split("\\|").each { f -> def.formats.add(f.trim()) }
                }
                // Validate enum/flag definitions before merging
                if (def.enumDefinition && attr.elements().size() > 0) {
                    def result = EnumFlagValidator.validate(def.enumDefinition, attr)
                    if (!result.isValid) {
                        throw new RuntimeException(result.error)
                    }
                } else if (attr.elements().size() > 0) {
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
                        if (def.enumDefinition) {
                            throw new RuntimeException(
                                "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                                "Definition with enum/flag: ${def.enumDefinition.asXML()}\n" +
                                "Attempted to merge with format: ${existingFormat}")
                        }
                        existingFormat.split("\\|").each { f -> def.formats.add(f.trim()) }
                    }
                } else {
                    rootAttr = def.enumDefinition ? def.enumDefinition.createCopy() : root.addElement("attr")
                    rootAttr.addAttribute("name", attrName)
                }

                // Set merged format if any
                if (!def.formats.isEmpty()) {
                    if (rootAttr.elements("enum").size() > 0 || rootAttr.elements("flag").size() > 0) {
                        throw new RuntimeException(
                            "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                            "Definition with enum/flag: ${rootAttr.asXML()}\n" +
                            "Attempted to merge with formats: ${def.formats.join('|')}")
                    }
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
