package com.allinweb.ch.util;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.w3c.dom.*;

public class CanonicalXmlNormalizer {

    // Attributes to KEEP
    private static final Set<String> CANONICAL_ATTRS =
            Set.of("class", "resource-id", "text", "content-desc", "clickable", "enabled", "bounds");

    // Attributes to DROP explicitly
    private static final String[] DROP_PREFIXES = {"a11y-", "drawing-", "accessibility-"};

    public static String normalize(String pageSourceXml) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document inputDoc = builder.parse(new ByteArrayInputStream(pageSourceXml.getBytes()));

        Document outputDoc = builder.newDocument();
        Element root = outputDoc.createElement("hierarchy");
        outputDoc.appendChild(root);

        normalizeNode(inputDoc.getDocumentElement(), outputDoc, root);

        return toString(outputDoc);
    }

    public static String normalizeHtmlToXhtml(String html) {
        if (html == null) return "";

        org.jsoup.nodes.Document doc = Jsoup.parse(html);

        // Make it XML-ish (XHTML) so javax.xml parsers can parse it
        doc.outputSettings(new org.jsoup.nodes.Document.OutputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8")
                .prettyPrint(false));

        return doc.outerHtml();
    }

    private static void normalizeNode(Node node, Document outDoc, Element outParent) {

        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        Element inElem = (Element) node;

        // Determine class
        String clazz = inElem.getAttribute("class");
        if (clazz.isEmpty()) {
            clazz = inElem.getNodeName();
        }

        // Skip invisible layout-only containers (optional rule)
        if (isLayoutContainer(clazz) && !hasUsefulAttributes(inElem)) {
            NodeList children = inElem.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                normalizeNode(children.item(i), outDoc, outParent);
            }
            return;
        }

        Element outElem = outDoc.createElement("element");
        outElem.setAttribute("class", clazz);

        NamedNodeMap attrs = inElem.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();

            if (CANONICAL_ATTRS.contains(name) && !shouldDrop(name)) {
                String value = attr.getNodeValue();
                if (!value.isEmpty()) {
                    outElem.setAttribute(name, value);
                }
            }
        }

        outParent.appendChild(outElem);

        NodeList children = inElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            normalizeNode(children.item(i), outDoc, outElem);
        }
    }

    private static boolean shouldDrop(String attrName) {
        for (String prefix : DROP_PREFIXES) {
            if (attrName.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean hasUsefulAttributes(Element e) {
        return !e.getAttribute("resource-id").isEmpty()
                || !e.getAttribute("text").isEmpty()
                || !e.getAttribute("content-desc").isEmpty()
                || "true".equals(e.getAttribute("clickable"));
    }

    private static boolean isLayoutContainer(String clazz) {
        return clazz.contains("Layout") || clazz.contains("ViewGroup") || clazz.equals("android.view.View");
    }

    private static String toString(Document doc) throws Exception {
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
