package com.devradar.ingest.deps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PomParser implements DependencyFileParser {

    private static final Logger LOG = LoggerFactory.getLogger(PomParser.class);

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

            NodeList deps = doc.getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                Element el = (Element) deps.item(i);
                String groupId = textContent(el, "groupId");
                String artifactId = textContent(el, "artifactId");
                String version = textContent(el, "version");

                if (groupId == null || artifactId == null || version == null) continue;
                if (version.contains("${")) continue;

                out.add(new ParsedDependency("MAVEN", groupId + ":" + artifactId, version));
            }
        } catch (Exception e) {
            LOG.warn("failed to parse pom.xml: {}", e.toString());
        }
        return out;
    }

    private static String textContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() ? null : text;
    }
}
