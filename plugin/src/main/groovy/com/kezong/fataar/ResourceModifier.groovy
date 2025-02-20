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
        boolean inRoot = false
        Element firstDeclareStyleableDefinition = null
        
        boolean hasEnumChildren() {
            return enumDefinition != null && enumDefinition.elements("enum").size() > 0
        }
        
        boolean hasFlagChildren() {
            return enumDefinition != null && enumDefinition.elements("flag").size() > 0
        }
        
        boolean hasValidFormat() {
            // Always allow no format (implicit)
            if (formats.isEmpty()) {
                return true
            }
            // Allow matching formats
            if (hasEnumChildren() && formats.contains("enum")) {
                return true
            }
            if (hasFlagChildren() && formats.contains("flags")) {
                return true
            }
            // Prevent mixing enum/flag with other formats
            if (hasEnumChildren() || hasFlagChildren()) {
                return false
            }
            return true
        }
    }

    private void processAttrDefinition(Element attr, AttrDefinition attrDef) {
        String format = attr.attributeValue("format")
        if (format) {
            format.split("\\|").each { f -> attrDef.formats.add(f.trim()) }
        }
        if (attr.elements("enum").size() > 0 || attr.elements("flag").size() > 0) {
            attrDef.enumDefinition = attr
        }
    }

    private void moveToRootIfNeeded(Element root, Map<String, AttrDefinition> attrDefinitions) {
        attrDefinitions.each { attrName, attrDef ->
            if (attrDef.shouldMoveToRoot()) {
                // Create or update root attr
                Element rootAttr = attrDef.enumDefinition ? 
                    attrDef.enumDefinition.createCopy() : 
                    root.addElement("attr")
                rootAttr.addAttribute("name", attrName)
                
                // Set merged formats
                if (!attrDef.formats.isEmpty()) {
                    rootAttr.addAttribute("format", attrDef.formats.join("|"))
                }
                
                // Update references
                root.elements("declare-styleable").each { styleable ->
                    styleable.elements("attr").findAll { 
                        it.attributeValue("name") == attrName 
                    }.each { attr ->
                        if (attr != attrDef.firstDeclareStyleableDefinition) {
                            attr.clearContent()
                            attr.attributes().removeIf { it.name != "name" }
                        }
                    }
                }
            }
        }
    }

    private void validateAndMergeDefinition(Element attr, AttrDefinition attrDef) {
        String format = attr.attributeValue("format")
        if (format) {
            format.split("\\|").each { f -> attrDef.formats.add(f.trim()) }
        }
        
        if (!attrDef.hasValidFormat()) {
            throw new RuntimeException(
                "Invalid format for attribute '${attr.attributeValue('name')}'"
            )
        }
        
        if (attr.elements("enum").size() > 0 || attr.elements("flag").size() > 0) {
            if (attrDef.enumDefinition) {
                def result = EnumFlagValidator.validate(attrDef.enumDefinition, attr)
                if (!result.isValid) {
                    throw new RuntimeException(result.error)
                }
            } else {
                attrDef.enumDefinition = attr
            }
        }
    }

    void processValuesXml(File valuesXml) {
        if (!valuesXml.exists()) {
            return
        }

        SAXReader reader = new SAXReader()
        Document document = reader.read(valuesXml)
        Element root = document.getRootElement()

        // First pass: collect all attr definitions
        Map<String, AttrDefinition> attrDefinitions = new HashMap<>()
        
        // Process root attrs first
        root.elements("attr").each { attr ->
            String attrName = attr.attributeValue("name")
            AttrDefinition attrDef = new AttrDefinition()
            attrDef.inRoot = true
            processAttrDefinition(attr, attrDef)
            attrDefinitions.put(attrName, attrDef)
        }
        
        // Process declare-styleable attrs
        root.elements("declare-styleable").each { styleable ->
            styleable.elements("attr").each { attr ->
                String attrName = attr.attributeValue("name")
                AttrDefinition attrDef = attrDefinitions.computeIfAbsent(
                    attrName, { k -> new AttrDefinition() }
                )
                attrDef.occurrences++
                if (!attrDef.inRoot && !attrDef.firstDeclareStyleableDefinition) {
                    attrDef.firstDeclareStyleableDefinition = attr
                }
                validateAndMergeDefinition(attr, attrDef)
            }
        }

        // Move only duplicate attrs to root level with merged formats
        moveToRootIfNeeded(root, attrDefinitions)

        // Write back the modified XML
        OutputFormat format = OutputFormat.createPrettyPrint()
        XMLWriter writer = new XMLWriter(new FileWriter(valuesXml), format)
        writer.write(document)
        writer.close()
    }

        // Move only duplicate attrs to root level with merged formats
        attrDefinitions.each { attrName, attrDef ->
            if (attrDef.occurrences > 1) {
                // Create or update root attr
                Element rootAttr
                List<Element> existingAttrs = root.elements("attr").findAll { it.attributeValue("name") == attrName }
                if (existingAttrs.size() > 0) {
                    rootAttr = existingAttrs.first()
                    // Merge formats with existing
                    String existingFormat = rootAttr.attributeValue("format")
                    if (existingFormat) {
                        if (attrDef.enumDefinition) {
                            throw new RuntimeException(
                                "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                                "Definition with enum/flag: ${attrDef.enumDefinition.asXML()}\n" +
                                "Attempted to merge with format: ${existingFormat}")
                        }
                        existingFormat.split("\\|").each { f -> attrDef.formats.add(f.trim()) }
                    }
                } else {
                    rootAttr = attrDef.enumDefinition ? attrDef.enumDefinition.createCopy() : root.addElement("attr")
                    rootAttr.addAttribute("name", attrName)
                }

                // Set merged format if any
                if (!attrDef.formats.isEmpty()) {
                    if (rootAttr.elements("enum").size() > 0 || rootAttr.elements("flag").size() > 0) {
                        throw new RuntimeException(
                            "Attribute '${attrName}' cannot mix enum/flag with other formats:\n" +
                            "Definition with enum/flag: ${rootAttr.asXML()}\n" +
                            "Attempted to merge with formats: ${attrDef.formats.join('|')}")
                    }
                    rootAttr.addAttribute("format", attrDef.formats.join("|"))
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
