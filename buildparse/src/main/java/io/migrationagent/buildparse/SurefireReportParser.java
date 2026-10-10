package io.migrationagent.buildparse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Parses Surefire's {@code TEST-*.xml} reports, which are the authoritative
 * source of test counts and pass/fail/error/skip status (see DESIGN.md
 * section 5.4 — console output is only used for things Surefire never
 * reports, namely compile errors).
 *
 * <p>Walks the whole repo tree rather than assuming a single module, so this
 * works unchanged for both a single-module project and a multi-module Maven
 * reactor (each module gets its own {@code target/surefire-reports/} dir).
 */
public final class SurefireReportParser {

    public record Result(
            int filesParsed,
            int total,
            int failed,
            int errored,
            int skipped,
            List<Failure> failures
    ) {
    }

    public Result parse(Path repoRoot) throws IOException {
        List<Path> reportFiles = findReportFiles(repoRoot);

        int total = 0;
        int failed = 0;
        int errored = 0;
        int skipped = 0;
        List<Failure> failures = new ArrayList<>();

        DocumentBuilder builder = newSafeDocumentBuilder();
        for (Path reportFile : reportFiles) {
            Document doc;
            try {
                doc = builder.parse(reportFile.toFile());
            } catch (Exception malformed) {
                // Adversarial or truncated XML degrades to "no data from this
                // file" rather than failing the whole run — see threat model
                // in DESIGN.md section 12 (harness parsing robustness).
                continue;
            }
            Element testsuite = doc.getDocumentElement();
            total += intAttr(testsuite, "tests");
            failed += intAttr(testsuite, "failures");
            errored += intAttr(testsuite, "errors");
            skipped += intAttr(testsuite, "skipped");
            failures.addAll(extractFailures(testsuite));
        }

        return new Result(reportFiles.size(), total, failed, errored, skipped, failures);
    }

    private List<Path> findReportFiles(Path repoRoot) throws IOException {
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            return walk
                    .filter(p -> p.getParent() != null
                            && p.getParent().getFileName().toString().equals("surefire-reports"))
                    .filter(p -> p.getFileName().toString().startsWith("TEST-")
                            && p.getFileName().toString().endsWith(".xml"))
                    .toList();
        }
    }

    private List<Failure> extractFailures(Element testsuite) {
        List<Failure> result = new ArrayList<>();
        NodeList testcases = testsuite.getElementsByTagName("testcase");
        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            Element failureNode = firstChildElement(testcase, "failure");
            Element errorNode = failureNode != null ? null : firstChildElement(testcase, "error");
            Element node = failureNode != null ? failureNode : errorNode;
            if (node == null) {
                continue;
            }
            String classname = testcase.getAttribute("classname");
            String testName = testcase.getAttribute("name");
            String type = node.getAttribute("type");
            String message = node.getAttribute("message");
            String rawExcerpt = node.getTextContent();

            FailureCategory category = FailureClassifier.classify(type, message)
                    .orElse(FailureCategory.TEST_FAILURE);

            result.add(new Failure(
                    category,
                    classname,
                    -1,
                    type,
                    (classname + "." + testName + ": " + message).trim(),
                    rawExcerpt
            ));
        }
        return result;
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                return (Element) child;
            }
        }
        return null;
    }

    private static int intAttr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * XXE-hardened parser: Surefire reports are generated by the target
     * repo's own build, which is untrusted input per the threat model
     * (DESIGN.md section 12) even though it's "our own" test tool output —
     * a malicious repo could still shape it. External entities and DOCTYPEs
     * are disabled rather than relying on the JDK's default configuration.
     */
    private static DocumentBuilder newSafeDocumentBuilder() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException("Failed to configure XML parser", e);
        }
    }
}
