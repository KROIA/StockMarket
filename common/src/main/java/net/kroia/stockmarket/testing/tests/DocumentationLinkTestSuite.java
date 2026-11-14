package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * SM-276: checks the repository's documentation offline ({@code gradlew :common:test}, no game):
 * <ul>
 *   <li>every relative markdown link, image link and {@code <img src>} in {@code README.md} and
 *       {@code documentation/**.md} points to an existing file, and a {@code #anchor} to an existing heading
 *       (GitHub heading slugs, or an explicit {@code id}/{@code name} anchor) of the target markdown file;</li>
 *   <li>every SVG under {@code documentation/images/plugins/} is well-formed XML with a {@code viewBox} or
 *       {@code width} and {@code height} on its root.</li>
 * </ul>
 * Whether the diagrams say the right thing is a docs review. The repository root is the system property
 * {@value #REPO_DIR_PROPERTY}, else the first parent of the working directory that holds {@code README.md} and
 * {@code documentation/} (Gradle runs the tests in {@code common/build/tmp/test}). Skips when neither is found
 * (in game the working directory is the instance folder).
 */
public class DocumentationLinkTestSuite extends TestSuite {

    /** System property naming the repository root (optional, see the class doc). */
    public static final String REPO_DIR_PROPERTY = "stockmarket.repoDir";

    /** {@code [text](target)} and {@code ![alt](target)}; the target ends at the first unbalanced ')'. */
    private static final Pattern MD_LINK = Pattern.compile("!?\\[[^\\]]*\\]\\(\\s*(<[^>]*>|[^)\\s]*)[^)]*\\)");
    private static final Pattern IMG_SRC = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ANCHOR = Pattern.compile("<a\\b[^>]*\\b(?:name|id)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DOCUMENTATION;
    }

    @Override
    public boolean needsGame() {
        return false; // runs offline in gradlew :common:test
    }

    @Override
    public void registerTests() {
        addTest("relative_links_resolve", this::test_relativeLinksResolve);
        addTest("plugin_svgs_well_formed", this::test_pluginSvgsWellFormed);
    }

    private TestResult test_relativeLinksResolve() {
        Path root = repoRoot();
        if (root == null)
            return skip("repository root not found (set -D" + REPO_DIR_PROPERTY + ")");
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("README.md"));
        try (Stream<Path> walk = Files.walk(root.resolve("documentation"))) {
            walk.filter(p -> p.toString().endsWith(".md")).sorted().forEach(files::add);
        } catch (Exception e) {
            return fail("cannot list documentation/: " + e);
        }
        Map<Path, Set<String>> anchorCache = new HashMap<>();
        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (Path file : files) {
            List<String> targets;
            try {
                targets = linkTargets(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                broken.add(root.relativize(file) + ": unreadable (" + e + ")");
                continue;
            }
            for (String target : targets) {
                String problem = check(file, target, anchorCache);
                checked++;
                if (problem != null)
                    broken.add(root.relativize(file).toString().replace('\\', '/') + ": " + target + " -> " + problem);
            }
        }
        if (!broken.isEmpty())
            return fail(broken.size() + " broken of " + checked + " links:\n  " + String.join("\n  ", broken));
        return pass(checked + " relative links in " + files.size() + " files resolve");
    }

    private TestResult test_pluginSvgsWellFormed() {
        Path root = repoRoot();
        if (root == null)
            return skip("repository root not found (set -D" + REPO_DIR_PROPERTY + ")");
        Path dir = root.resolve("documentation/images/plugins");
        if (!Files.isDirectory(dir))
            return skip("no documentation/images/plugins/");
        List<Path> svgs;
        try (Stream<Path> walk = Files.walk(dir)) {
            svgs = walk.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".svg")).sorted().toList();
        } catch (Exception e) {
            return fail("cannot list " + dir + ": " + e);
        }
        List<String> bad = new ArrayList<>();
        for (Path svg : svgs) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                // no DTD or external entity loading: only well-formedness is checked
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setExpandEntityReferences(false);
                Element svgRoot = factory.newDocumentBuilder().parse(svg.toFile()).getDocumentElement();
                if (!"svg".equals(svgRoot.getLocalName()))
                    bad.add(svg.getFileName() + ": root is <" + svgRoot.getTagName() + ">");
                else if (!svgRoot.hasAttribute("viewBox") && !(svgRoot.hasAttribute("width") && svgRoot.hasAttribute("height")))
                    bad.add(svg.getFileName() + ": no viewBox and no width/height");
            } catch (Exception e) {
                bad.add(svg.getFileName() + ": " + e.getMessage());
            }
        }
        if (svgs.isEmpty())
            return skip("no SVG in documentation/images/plugins/");
        if (!bad.isEmpty())
            return fail(bad.size() + " of " + svgs.size() + " SVGs:\n  " + String.join("\n  ", bad));
        return pass(svgs.size() + " SVGs well-formed with a size");
    }

    // ------------------------------------------------------------------ helpers

    /** @return the repository root, or null if not configured and not found above the working directory */
    private static @Nullable Path repoRoot() {
        String configured = System.getProperty(REPO_DIR_PROPERTY);
        if (configured != null && !configured.isBlank())
            return isRepoRoot(Paths.get(configured)) ? Paths.get(configured).toAbsolutePath() : null;
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent())
            if (isRepoRoot(p))
                return p;
        return null;
    }

    private static boolean isRepoRoot(Path p) {
        return Files.isRegularFile(p.resolve("README.md")) && Files.isDirectory(p.resolve("documentation"));
    }

    /** @return the link and image targets of a markdown file, outside fenced code blocks and inline code */
    static List<String> linkTargets(List<String> lines) {
        List<String> out = new ArrayList<>();
        boolean fenced = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (fenced)
                continue;
            String text = INLINE_CODE.matcher(line).replaceAll("");
            Matcher m = MD_LINK.matcher(text);
            while (m.find())
                out.add(m.group(1).startsWith("<") ? m.group(1).substring(1, m.group(1).length() - 1) : m.group(1));
            m = IMG_SRC.matcher(text);
            while (m.find())
                out.add(m.group(1));
        }
        return out;
    }

    /** @return null if {@code target} (as written in {@code file}) resolves, else what is wrong */
    private static @Nullable String check(Path file, String target, Map<Path, Set<String>> anchorCache) {
        if (target.isEmpty())
            return "empty link";
        if (target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || target.startsWith("//"))
            return null; // absolute URL (http:, mailto:, ...): not checked here
        int hash = target.indexOf('#');
        String pathPart = hash < 0 ? target : target.substring(0, hash);
        String anchor = hash < 0 ? null : target.substring(hash + 1);
        int query = pathPart.indexOf('?');
        if (query >= 0)
            pathPart = pathPart.substring(0, query);
        Path resolved = file;
        if (!pathPart.isEmpty()) {
            String decoded = URLDecoder.decode(pathPart.replace("+", "%2B"), StandardCharsets.UTF_8);
            resolved = file.getParent().resolve(decoded).normalize();
            if (!Files.exists(resolved))
                return "missing file";
        }
        if (anchor == null || anchor.isEmpty() || !resolved.toString().endsWith(".md"))
            return null;
        Set<String> anchors = anchorCache.computeIfAbsent(resolved, DocumentationLinkTestSuite::anchors);
        return anchors.contains(anchor.toLowerCase(Locale.ROOT)) ? null : "missing anchor";
    }

    /** @return the heading anchors (GitHub slugs, with -1, -2 for repeats) and explicit HTML anchors of a markdown file */
    private static Set<String> anchors(Path file) {
        Set<String> out = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return out;
        }
        boolean fenced = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (fenced)
                continue;
            Matcher a = HTML_ANCHOR.matcher(line);
            while (a.find())
                out.add(a.group(1).toLowerCase(Locale.ROOT));
            Matcher h = HEADING.matcher(line);
            if (!h.matches())
                continue;
            String slug = slug(h.group(2));
            int n = seen.merge(slug, 1, Integer::sum) - 1;
            out.add(n == 0 ? slug : slug + "-" + n);
        }
        return out;
    }

    /** GitHub's heading slug: link text only, lowercase, punctuation except '-' and '_' dropped, spaces to '-'. */
    static String slug(String heading) {
        String text = heading.replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1") // links -> their text
                .replaceAll("<[^>]+>", ""); // inline HTML tags
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M} _-]", "")
                .replace(' ', '-');
    }
}
