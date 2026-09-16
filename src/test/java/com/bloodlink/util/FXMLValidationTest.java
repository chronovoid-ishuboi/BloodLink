package com.bloodlink.util;

import com.bloodlink.Main;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks on the FXML views and the controllers behind them.
 * <p>
 * This used to assert only that the five files existed on the classpath, which
 * could not fail for any reason a developer would care about. The interesting
 * failures in FXML are all of the "compiles fine, explodes at runtime" kind:
 * a renamed {@code fx:id} the controller no longer matches, a handler wired to a
 * method that was deleted, a style class with a typo, a duplicated id. None of
 * that is visible to {@code javac}, and none of it shows up until the screen is
 * opened by hand.
 * <p>
 * These tests parse the FXML as XML and reflect over the controllers, so they
 * need no JavaFX toolkit and run headless on any machine. Each one encodes a
 * real defect found in this codebase:
 * <ul>
 *   <li>{@code donor_dashboard.fxml} declared {@code fx:id="bloodGroupLabel"}
 *       twice; FXML keeps the last, so the Overview stat tile was never
 *       populated and sat on its placeholder.</li>
 *   <li>Two-class {@code styleClass} attributes written space-separated silently
 *       matched no rule at all, because FXML treats the attribute as a
 *       comma-delimited list.</li>
 * </ul>
 */
class FXMLValidationTest {

    private static final String VIEW_DIR = "/com/bloodlink/view/";
    private static final List<String> FXML_FILES = List.of(
            "login.fxml", "register.fxml", "admin_dashboard.fxml",
            "donor_dashboard.fxml", "requester_dashboard.fxml");

    @Test
    void allFXMLResourcesExist() {
        for (String fxml : FXML_FILES) {
            assertNotNull(Main.class.getResource(VIEW_DIR + fxml), "FXML file missing: " + fxml);
        }
    }

    @Test
    void allFXMLFilesAreWellFormedXml() {
        for (String fxml : FXML_FILES) {
            assertDoesNotThrow(() -> parse(fxml), fxml + " is not well-formed XML");
        }
    }

    /** A duplicate id silently wins over the earlier one, leaving a control permanently unbound. */
    @Test
    void noFxIdIsDeclaredTwiceInTheSameFile() throws Exception {
        for (String fxml : FXML_FILES) {
            List<String> ids = allFxIds(parse(fxml));
            Set<String> duplicates = new LinkedHashSet<>();
            Set<String> seen = new HashSet<>();
            for (String id : ids) if (!seen.add(id)) duplicates.add(id);
            assertTrue(duplicates.isEmpty(),
                    fxml + " declares these fx:id values more than once: " + duplicates
                            + ". FXML injects only the last one, so the earlier control is never bound.");
        }
    }

    /** Every fx:id must have somewhere to be injected, or loading the view throws. */
    @Test
    void everyFxIdHasAMatchingControllerField() throws Exception {
        for (String fxml : FXML_FILES) {
            Document document = parse(fxml);
            Class<?> controller = controllerOf(document, fxml);
            Set<String> fields = new HashSet<>();
            for (Class<?> type = controller; type != null && type != Object.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) fields.add(field.getName());
            }
            for (String id : allFxIds(document)) {
                assertTrue(fields.contains(id),
                        fxml + " has fx:id=\"" + id + "\" but " + controller.getSimpleName()
                                + " has no field of that name.");
            }
        }
    }

    /** A handler attribute pointing at a method that does not exist fails only when the control is used. */
    @Test
    void everyEventHandlerResolvesToAControllerMethod() throws Exception {
        for (String fxml : FXML_FILES) {
            Document document = parse(fxml);
            Class<?> controller = controllerOf(document, fxml);
            Set<String> methods = new HashSet<>();
            for (Class<?> type = controller; type != null && type != Object.class; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) methods.add(method.getName());
            }
            for (Map.Entry<String, String> handler : allHandlers(document).entrySet()) {
                assertTrue(methods.contains(handler.getValue()),
                        fxml + " wires " + handler.getKey() + "=\"#" + handler.getValue() + "\" but "
                                + controller.getSimpleName() + " has no such method.");
            }
        }
    }

    /**
     * Catches both typos and the space-versus-comma trap: FXML parses
     * {@code styleClass} as a comma-delimited list, so {@code "a b"} is one class
     * named "a b" which matches nothing and fails completely silently.
     */
    @Test
    void everyStyleClassUsedInFxmlExistsInTheStylesheet() throws Exception {
        Set<String> defined = styleClassesDefinedIn(readResource("/com/bloodlink/css/theme.css"));
        assertFalse(defined.isEmpty(), "No style classes were parsed out of theme.css");

        for (String fxml : FXML_FILES) {
            for (String styleClass : allStyleClasses(parse(fxml))) {
                assertFalse(styleClass.contains(" "),
                        fxml + " has the style class \"" + styleClass + "\", which contains a space. "
                                + "FXML splits styleClass on commas, not spaces, so this matches no rule at all. "
                                + "Write it as styleClass=\"a, b\".");
                assertTrue(defined.contains(styleClass),
                        fxml + " uses style class \"" + styleClass + "\", which theme.css does not define.");
            }
        }
    }

    /** Every view must pull in the stylesheet, or it renders unstyled. */
    @Test
    void everyViewAttachesTheStylesheet() throws Exception {
        for (String fxml : FXML_FILES) {
            String stylesheets = parse(fxml).getDocumentElement().getAttribute("stylesheets");
            assertTrue(stylesheets.contains("theme.css"),
                    fxml + " does not attach theme.css on its root element.");
        }
    }

    // --- helpers ------------------------------------------------------------

    private static Document parse(String fxml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // These are local project files, but disabling external entity resolution
        // keeps the parser from reaching out over the network for a DTD.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = Main.class.getResourceAsStream(VIEW_DIR + fxml)) {
            assertNotNull(in, "FXML file missing: " + fxml);
            return builder.parse(in);
        }
    }

    private static Class<?> controllerOf(Document document, String fxml) throws ClassNotFoundException {
        String name = document.getDocumentElement().getAttributeNS("http://javafx.com/fxml/1", "controller");
        assertNotNull(name, fxml + " declares no fx:controller");
        assertFalse(name.isBlank(), fxml + " declares no fx:controller");
        return Class.forName(name);
    }

    private static List<String> allFxIds(Document document) {
        List<String> ids = new ArrayList<>();
        forEachElement(document, element -> {
            String id = element.getAttributeNS("http://javafx.com/fxml/1", "id");
            if (id != null && !id.isBlank()) ids.add(id);
        });
        return ids;
    }

    /** Attribute name to handler method, for every attribute whose value starts with '#'. */
    private static Map<String, String> allHandlers(Document document) {
        Map<String, String> handlers = new LinkedHashMap<>();
        forEachElement(document, element -> {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String value = attribute.getNodeValue();
                if (value != null && value.startsWith("#") && value.length() > 1) {
                    handlers.put(attribute.getNodeName(), value.substring(1));
                }
            }
        });
        return handlers;
    }

    private static Set<String> allStyleClasses(Document document) {
        Set<String> classes = new LinkedHashSet<>();
        forEachElement(document, element -> {
            String value = element.getAttribute("styleClass");
            if (value == null || value.isBlank()) return;
            for (String token : value.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) classes.add(trimmed);
            }
        });
        return classes;
    }

    private static void forEachElement(Document document, java.util.function.Consumer<Element> action) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) action.accept(element);
        }
    }

    /** Pulls every {@code .class-name} selector out of the stylesheet, ignoring comments. */
    private static Set<String> styleClassesDefinedIn(String css) {
        String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "");
        Set<String> classes = new HashSet<>();
        Matcher matcher = Pattern.compile("\\.([A-Za-z][A-Za-z0-9_-]*)").matcher(withoutComments);
        while (matcher.find()) classes.add(matcher.group(1));
        return classes;
    }

    private static String readResource(String resource) throws Exception {
        try (InputStream in = Main.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
