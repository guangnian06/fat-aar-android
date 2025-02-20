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
            
            // Allow matching formats (enum with enum, flags with flags)
            if (enums1.size() > 0 && format1 && !format1.equals("enum")) {
                return new ValidationResult(false,
                    "Attribute '${attr1.attributeValue('name')}' with enum children must have format='enum' or no format:\n" +
                    "Definition: ${attr1.asXML()}")
            }
            if (enums2.size() > 0 && format2 && !format2.equals("enum")) {
                return new ValidationResult(false,
                    "Attribute '${attr2.attributeValue('name')}' with enum children must have format='enum' or no format:\n" +
                    "Definition: ${attr2.asXML()}")
            }
            if (flags1.size() > 0 && format1 && !format1.equals("flags")) {
                return new ValidationResult(false,
                    "Attribute '${attr1.attributeValue('name')}' with flag children must have format='flags' or no format:\n" +
                    "Definition: ${attr1.asXML()}")
            }
            if (flags2.size() > 0 && format2 && !format2.equals("flags")) {
                return new ValidationResult(false,
                    "Attribute '${attr2.attributeValue('name')}' with flag children must have format='flags' or no format:\n" +
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
        Element firstDefinition = null
        List<Element> declareStyleableElements = new ArrayList<>()
        
        boolean isEnumOrFlag() {
            return enumDefinition != null && 
                   (enumDefinition.elements("enum").size() > 0 || 
                    enumDefinition.elements("flag").size() > 0)
        }
    }

    private void processAttrDefinition(Element attr, AttrDefinition attrDef) {
        String format = attr.attributeValue("format")
        def enums = attr.elements("enum")
        def flags = attr.elements("flag")
        
        // Handle format validation
        if (format) {
            // Check for enum/flags format compatibility
            if (enums.size() > 0) {
                // Allow format="enum", but prevent mixing with other formats
                if (!format.equals("enum")) {
                    throw new RuntimeException(
                        "Attribute '${attr.attributeValue('name')}' with enum children must have format='enum' or no format:\n" +
                        "Definition: ${attr.asXML()}")
                }
            } else if (flags.size() > 0) {
                // Allow format="flags", but prevent mixing with other formats
                if (!format.equals("flags")) {
                    throw new RuntimeException(
                        "Attribute '${attr.attributeValue('name')}' with flag children must have format='flags' or no format:\n" +
                        "Definition: ${attr.asXML()}")
                }
            }
            
            // Add formats after validation
            format.split("\\|").each { f -> attrDef.formats.add(f.trim()) }
        }
        
        // Store enum/flag definition
        if (enums.size() > 0 || flags.size() > 0) {
            attrDef.enumDefinition = attr
        }
        
        // Track first definition
        if (attrDef.firstDefinition == null) {
            attrDef.firstDefinition = attr
        }
        
        // Add to declare-styleable elements if not already tracked
        if (!attrDef.declareStyleableElements.contains(attr)) {
            attrDef.declareStyleableElements.add(attr)
        }
    }

    private void validateAndMergeDefinition(Element attr, AttrDefinition attrDef) {
        if (attrDef.enumDefinition != null && attr.elements().size() > 0) {
            def result = EnumFlagValidator.validate(attrDef.enumDefinition, attr)
            if (!result.isValid) {
                throw new RuntimeException(result.error)
            }
        } else {
            processAttrDefinition(attr, attrDef)
        }
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

        // Process root attrs first
        root.elements("attr").each { attr ->
            String attrName = attr.attributeValue("name")
            AttrDefinition attrDef = new AttrDefinition()
            processAttrDefinition(attr, attrDef)
            attrDefinitions.put(attrName, attrDef)
        }

        // Process declare-styleable attrs in single pass
        root.elements("declare-styleable").each { styleable ->
            styleable.elements("attr").each { attr ->
                String attrName = attr.attributeValue("name")
                AttrDefinition attrDef = attrDefinitions.computeIfAbsent(attrName, { k -> new AttrDefinition() })
                
                // Skip reference-only attrs
                if (attr.attributes().size() == 1 && attr.elements().size() == 0) {
                    attrDef.occurrences++
                    return
                }
                
                // Process definition
                if (attrDef.enumDefinition != null && attr.elements().size() > 0) {
                    // Validate enum/flag definitions match
                    def result = EnumFlagValidator.validate(attrDef.enumDefinition, attr)
                    if (!result.isValid) {
                        throw new RuntimeException(result.error)
                    }
                } else {
                    processAttrDefinition(attr, attrDef)
                }
                
                attrDef.occurrences++
                attrDef.declareStyleableElements.add(attr)
            }
        }

        // Move duplicate attrs to root level if they're all in declare-styleable
        attrDefinitions.each { attrName, attrDef ->
            // Skip if not a duplicate
            if (attrDef.occurrences <= 1) {
                return
            }

            // Check if all definitions are in declare-styleable
            List<Element> rootAttrs = root.elements("attr").findAll { it.attributeValue("name") == attrName }
            if (rootAttrs.isEmpty()) {
                // Create root attr from first definition
                Element rootAttr = attrDef.enumDefinition ? 
                    attrDef.enumDefinition.createCopy() : 
                    attrDef.firstDefinition.createCopy()
                rootAttr.addAttribute("name", attrName)
                root.add(rootAttr)

                // Update all references using collected elements
                attrDef.declareStyleableElements.each { attr ->
                    attr.clearContent()
                    attr.attributes().removeIf { it.name != "name" }
                }
            } else {
                // Root definition exists, just update references
                attrDef.declareStyleableElements.each { attr ->
                    attr.clearContent()
                    attr.attributes().removeIf { it.name != "name" }
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
