import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CodebaseAnalyzer - A utility to help understand and visualize Java codebases
 * Can be run without external dependencies or AI tools.
 */
public class CodebaseAnalyzer {
    private static final String OUTPUT_DIR = "code-analysis";
    private static final String API_SUMMARY_FILE = "api-summary.md";
    private static final String CLASS_HIERARCHY_FILE = "class-hierarchy.md";
    private static final String METHOD_DETAILS_DIR = "method-details";
    private static final String ENUM_SUMMARY_FILE = "enum-summary.md";
    
    private static Map<String, ClassInfo> classInfoMap = new HashMap<>();
    private static Map<String, List<String>> packageToClassMap = new HashMap<>();
    private static List<ApiEndpoint> apiEndpoints = new ArrayList<>();
    private static Map<String, List<String>> enumMap = new HashMap<>();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java CodebaseAnalyzer <source-directory> [specific-class]");
            System.out.println("Options:");
            System.out.println("  source-directory: Root directory of Java source code");
            System.out.println("  specific-class: (Optional) Analyze only a specific class");
            return;
        }
        
        String rootDir = args[0];
        String specificClass = args.length > 1 ? args[1] : null;
        
        System.out.println("Analyzing codebase in: " + rootDir);
        createOutputDirectories();
        
        try {
            List<File> javaFiles;
            if (specificClass != null) {
                javaFiles = findSpecificJavaFile(rootDir, specificClass);
                System.out.println("Analyzing specific class: " + specificClass);
            } else {
                javaFiles = findAllJavaFiles(rootDir);
                System.out.println("Found " + javaFiles.size() + " Java files");
            }
            
            // First pass: collect class information
            for (File file : javaFiles) {
                parseJavaFile(file);
            }
            
            // Second pass: build hierarchy and relationships
            buildRelationships();
            
            // Generate reports
            generateApiSummary();
            generateClassHierarchy();
            generateEnumSummary();
            generateMethodDetails();
            
            System.out.println("Analysis complete. Reports saved to " + OUTPUT_DIR + " directory");
            
        } catch (IOException e) {
            System.err.println("Error analyzing codebase: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<File> findAllJavaFiles(String rootDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(rootDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
    
    private static List<File> findSpecificJavaFile(String rootDir, String className) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(rootDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.getFileName().toString().equals(className + ".java") || 
                             p.getFileName().toString().matches(".*" + className + "\\.java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
    
    private static void parseJavaFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            String packageName = extractPackageName(content);
            String className = file.getName().replace(".java", "");
            
            // Add to package map
            packageToClassMap.putIfAbsent(packageName, new ArrayList<>());
            packageToClassMap.get(packageName).add(className);
            
            // Create class info
            ClassInfo classInfo = new ClassInfo();
            classInfo.name = className;
            classInfo.packageName = packageName;
            classInfo.filePath = file.getPath();
            classInfo.superClass = extractSuperClass(content);
            classInfo.interfaces = extractInterfaces(content);
            classInfo.methods = extractMethods(content);
            classInfo.isInterface = content.matches("(?s).*\\binterface\\s+" + className + "\\b.*");
            classInfo.isAbstract = content.matches("(?s).*\\babstract\\s+class\\s+" + className + "\\b.*");
            
            // Check if it's a Jersey REST resource
            checkForRestResource(content, classInfo);
            
            // Extract enums
            extractEnums(content, packageName);
            
            classInfoMap.put(packageName + "." + className, classInfo);
            
        } catch (IOException e) {
            System.err.println("Error parsing file " + file.getPath() + ": " + e.getMessage());
        }
    }
    
    private static String extractPackageName(String content) {
        Pattern pattern = Pattern.compile("package\\s+([\\w\\.]+)\\s*;");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "default";
    }
    
    private static String extractSuperClass(String content) {
        Pattern pattern = Pattern.compile("class\\s+\\w+\\s+extends\\s+([\\w\\.]+)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private static List<String> extractInterfaces(String content) {
        List<String> interfaces = new ArrayList<>();
        Pattern pattern = Pattern.compile("(implements|extends)\\s+([\\w\\.,\\s]+)\\{");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String[] interfaceList = matcher.group(2).split(",");
            for (String iface : interfaceList) {
                interfaces.add(iface.trim());
            }
        }
        
        return interfaces;
    }
    
    private static List<MethodInfo> extractMethods(String content) {
        List<MethodInfo> methods = new ArrayList<>();
        
        // Match method declarations
        Pattern methodPattern = Pattern.compile(
            "(?:public|protected|private|static|\\s) +(?:[\\w\\<\\>\\[\\]]+\\s+)+(\\w+) *\\([^)]*\\) *(?:throws [^{]+)?\\{",
            Pattern.MULTILINE
        );
        
        Matcher methodMatcher = methodPattern.matcher(content);
        
        while (methodMatcher.find()) {
            String methodDeclaration = content.substring(Math.max(0, methodMatcher.start() - 100), 
                                                         methodMatcher.end());
            
            MethodInfo method = new MethodInfo();
            method.name = methodMatcher.group(1);
            method.signature = extractMethodSignature(methodDeclaration);
            method.javadoc = extractJavadoc(content, methodMatcher.start() - 100, methodMatcher.start());
            method.annotations = extractAnnotations(methodDeclaration);
            
            methods.add(method);
        }
        
        return methods;
    }
    
    private static String extractMethodSignature(String methodDeclaration) {
        // Simplified signature extraction - in real use this would be more robust
        String[] lines = methodDeclaration.split("\n");
        for (String line : lines) {
            if (line.contains("(") && line.contains(")") && !line.trim().startsWith("//") && !line.trim().startsWith("*")) {
                return line.trim();
            }
        }
        return "";
    }
    
    private static String extractJavadoc(String content, int start, int end) {
        String section = content.substring(Math.max(0, start), end);
        Pattern javadocPattern = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
        Matcher javadocMatcher = javadocPattern.matcher(section);
        
        if (javadocMatcher.find()) {
            return javadocMatcher.group(1).trim();
        }
        return "";
    }
    
    private static List<String> extractAnnotations(String methodDeclaration) {
        List<String> annotations = new ArrayList<>();
        Pattern annotationPattern = Pattern.compile("@([\\w\\.]+)(?:\\([^)]*\\))?");
        Matcher annotationMatcher = annotationPattern.matcher(methodDeclaration);
        
        while (annotationMatcher.find()) {
            annotations.add(annotationMatcher.group(0));
        }
        
        return annotations;
    }
    
    private static void checkForRestResource(String content, ClassInfo classInfo) {
        // Check for class-level REST annotations
        boolean isRestResource = content.contains("@Path") || 
                                 content.contains("@Consumes") || 
                                 content.contains("@Produces");
        
        classInfo.isRestResource = isRestResource;
        
        if (isRestResource) {
            // Extract class-level @Path
            Pattern pathPattern = Pattern.compile("@Path\\(\"([^\"]+)\"\\)");
            Matcher pathMatcher = pathPattern.matcher(content);
            String classPath = pathMatcher.find() ? pathMatcher.group(1) : "";
            
            // Find REST methods
            for (MethodInfo method : classInfo.methods) {
                String httpMethod = null;
                String methodPath = "";
                
                // Check for REST method annotations
                for (String annotation : method.annotations) {
                    if (annotation.startsWith("@GET")) httpMethod = "GET";
                    else if (annotation.startsWith("@POST")) httpMethod = "POST";
                    else if (annotation.startsWith("@PUT")) httpMethod = "PUT";
                    else if (annotation.startsWith("@DELETE")) httpMethod = "DELETE";
                    else if (annotation.startsWith("@Path")) {
                        Pattern methodPathPattern = Pattern.compile("@Path\\(\"([^\"]+)\"\\)");
                        Matcher methodPathMatcher = methodPathPattern.matcher(annotation);
                        if (methodPathMatcher.find()) {
                            methodPath = methodPathMatcher.group(1);
                        }
                    }
                }
                
                if (httpMethod != null) {
                    ApiEndpoint endpoint = new ApiEndpoint();
                    endpoint.httpMethod = httpMethod;
                    endpoint.path = (classPath + (methodPath.isEmpty() ? "" : "/" + methodPath))
                                     .replaceAll("//", "/");
                    endpoint.methodName = method.name;
                    endpoint.className = classInfo.name;
                    endpoint.packageName = classInfo.packageName;
                    
                    // Check for consumes/produces
                    for (String annotation : method.annotations) {
                        if (annotation.startsWith("@Consumes")) {
                            Pattern consumesPattern = Pattern.compile("@Consumes\\(\"([^\"]+)\"\\)");
                            Matcher consumesMatcher = consumesPattern.matcher(annotation);
                            if (consumesMatcher.find()) {
                                endpoint.consumes = consumesMatcher.group(1);
                            }
                        } else if (annotation.startsWith("@Produces")) {
                            Pattern producesPattern = Pattern.compile("@Produces\\(\"([^\"]+)\"\\)");
                            Matcher producesMatcher = producesPattern.matcher(annotation);
                            if (producesMatcher.find()) {
                                endpoint.produces = producesMatcher.group(1);
                            }
                        }
                    }
                    
                    apiEndpoints.add(endpoint);
                }
            }
        }
    }
    
    private static void extractEnums(String content, String packageName) {
        Pattern enumPattern = Pattern.compile("enum\\s+(\\w+)\\s*\\{([^}]+)\\}");
        Matcher enumMatcher = enumPattern.matcher(content);
        
        while (enumMatcher.find()) {
            String enumName = enumMatcher.group(1);
            String enumValues = enumMatcher.group(2);
            
            List<String> values = Arrays.stream(enumValues.split(","))
                                      .map(String::trim)
                                      .filter(s -> !s.isEmpty())
                                      .collect(Collectors.toList());
            
            enumMap.put(packageName + "." + enumName, values);
        }
    }
    
    private static void buildRelationships() {
        // Set up inheritance relationships
        for (ClassInfo classInfo : classInfoMap.values()) {
            if (classInfo.superClass != null) {
                String fullSuperName = resolveFullClassName(classInfo.superClass, classInfo.packageName);
                classInfo.superClass = fullSuperName;
                
                ClassInfo superClass = classInfoMap.get(fullSuperName);
                if (superClass != null) {
                    superClass.subClasses.add(classInfo.packageName + "." + classInfo.name);
                }
            }
        }
    }
    
    private static String resolveFullClassName(String className, String currentPackage) {
        // If already fully qualified
        if (className.contains(".")) {
            return className;
        }
        
        // Try in current package
        String fullName = currentPackage + "." + className;
        if (classInfoMap.containsKey(fullName)) {
            return fullName;
        }
        
        // Check all packages (basic import resolution)
        for (String pkg : packageToClassMap.keySet()) {
            if (packageToClassMap.get(pkg).contains(className)) {
                return pkg + "." + className;
            }
        }
        
        // Couldn't resolve, return as is
        return className;
    }
    
    private static void generateApiSummary() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# API Endpoints Summary\n\n");
        
        // Group by package
        Map<String, List<ApiEndpoint>> packageGroups = apiEndpoints.stream()
            .collect(Collectors.groupingBy(e -> e.packageName));
        
        for (String pkg : packageGroups.keySet()) {
            sb.append("## Package: ").append(pkg).append("\n\n");
            
            List<ApiEndpoint> endpoints = packageGroups.get(pkg);
            // Group by class within package
            Map<String, List<ApiEndpoint>> classGroups = endpoints.stream()
                .collect(Collectors.groupingBy(e -> e.className));
            
            for (String className : classGroups.keySet()) {
                sb.append("### Class: ").append(className).append("\n\n");
                sb.append("| Method | Path | HTTP Method | Consumes | Produces |\n");
                sb.append("|--------|------|------------|----------|----------|\n");
                
                for (ApiEndpoint endpoint : classGroups.get(className)) {
                    sb.append("| ").append(endpoint.methodName).append(" | ")
                      .append(endpoint.path).append(" | ")
                      .append(endpoint.httpMethod).append(" | ")
                      .append(endpoint.consumes == null ? "" : endpoint.consumes).append(" | ")
                      .append(endpoint.produces == null ? "" : endpoint.produces).append(" |\n");
                }
                
                sb.append("\n");
            }
        }
        
        writeToFile(API_SUMMARY_FILE, sb.toString());
    }
    
    private static void generateClassHierarchy() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Class Hierarchy\n\n");
        
        // Find root classes (those with no super class in our codebase)
        List<ClassInfo> rootClasses = classInfoMap.values().stream()
            .filter(c -> c.superClass == null || !classInfoMap.containsKey(c.superClass))
            .collect(Collectors.toList());
        
        // First generate package summary
        sb.append("## Package Summary\n\n");
        List<String> sortedPackages = new ArrayList<>(packageToClassMap.keySet());
        Collections.sort(sortedPackages);
        
        for (String pkg : sortedPackages) {
            sb.append("### ").append(pkg).append("\n\n");
            List<String> classes = packageToClassMap.get(pkg);
            Collections.sort(classes);
            
            for (String className : classes) {
                String fullName = pkg + "." + className;
                ClassInfo info = classInfoMap.get(fullName);
                if (info != null) {
                    String typeIndicator = info.isInterface ? "[Interface] " : 
                                         info.isAbstract ? "[Abstract] " :
                                         info.isRestResource ? "[REST] " : "";
                    sb.append("- ").append(typeIndicator).append(className);
                    
                    // Add inheritance info
                    if (info.superClass != null && !info.superClass.equals("java.lang.Object")) {
                        sb.append(" extends ").append(simplifyClassName(info.superClass));
                    }
                    if (!info.interfaces.isEmpty()) {
                        sb.append(" implements ");
                        sb.append(info.interfaces.stream()
                            .map(CodebaseAnalyzer::simplifyClassName)
                            .collect(Collectors.joining(", ")));
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        // Then generate inheritance tree
        sb.append("## Inheritance Tree\n\n");
        for (ClassInfo rootClass : rootClasses) {
            printClassHierarchy(sb, rootClass, 0);
        }
        
        writeToFile(CLASS_HIERARCHY_FILE, sb.toString());
    }
    
    private static void printClassHierarchy(StringBuilder sb, ClassInfo classInfo, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        
        String typeIndicator = classInfo.isInterface ? "[Interface] " : 
                             classInfo.isAbstract ? "[Abstract] " :
                             classInfo.isRestResource ? "[REST] " : "";
        
        sb.append("- ").append(typeIndicator).append(classInfo.name);
        
        if (!classInfo.interfaces.isEmpty()) {
            sb.append(" implements ");
            sb.append(classInfo.interfaces.stream()
                .map(CodebaseAnalyzer::simplifyClassName)
                .collect(Collectors.joining(", ")));
        }
        
        sb.append("\n");
        
        // Print subclasses
        for (String subClassName : classInfo.subClasses) {
            ClassInfo subClass = classInfoMap.get(subClassName);
            if (subClass != null) {
                printClassHierarchy(sb, subClass, depth + 1);
            }
        }
    }
    
    private static void generateEnumSummary() throws IOException {
        if (enumMap.isEmpty()) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("# Enum Summary\n\n");
        
        List<String> sortedEnums = new ArrayList<>(enumMap.keySet());
        Collections.sort(sortedEnums);
        
        for (String enumName : sortedEnums) {
            String shortName = enumName.substring(enumName.lastIndexOf(".") + 1);
            String packageName = enumName.substring(0, enumName.lastIndexOf("."));
            
            sb.append("## ").append(shortName).append("\n\n");
            sb.append("Package: `").append(packageName).append("`\n\n");
            sb.append("Values:\n\n");
            
            List<String> values = enumMap.get(enumName);
            for (String value : values) {
                sb.append("- `").append(value).append("`\n");
            }
            
            sb.append("\n");
        }
        
        writeToFile(ENUM_SUMMARY_FILE, sb.toString());
    }
    
    private static void generateMethodDetails() throws IOException {
        File methodDir = new File(OUTPUT_DIR, METHOD_DETAILS_DIR);
        if (!methodDir.exists()) {
            methodDir.mkdir();
        }
        
        for (ClassInfo classInfo : classInfoMap.values()) {
            String className = classInfo.name;
            String packageName = classInfo.packageName;
            
            StringBuilder sb = new StringBuilder();
            sb.append("# Methods for ").append(className).append("\n\n");
            sb.append("Package: `").append(packageName).append("`\n\n");
            
            if (classInfo.superClass != null && !classInfo.superClass.equals("java.lang.Object")) {
                sb.append("Extends: `").append(classInfo.superClass).append("`\n\n");
            }
            
            if (!classInfo.interfaces.isEmpty()) {
                sb.append("Implements: ");
                sb.append(classInfo.interfaces.stream()
                    .map(i -> "`" + i + "`")
                    .collect(Collectors.joining(", ")));
                sb.append("\n\n");
            }
            
            if (classInfo.isRestResource) {
                sb.append("**This is a REST Resource**\n\n");
                
                // Find endpoints for this class
                List<ApiEndpoint> endpoints = apiEndpoints.stream()
                    .filter(e -> e.className.equals(className) && e.packageName.equals(packageName))
                    .collect(Collectors.toList());
                
                if (!endpoints.isEmpty()) {
                    sb.append("## REST Endpoints\n\n");
                    sb.append("| Method | Path | HTTP Method | Consumes | Produces |\n");
                    sb.append("|--------|------|------------|----------|----------|\n");
                    
                    for (ApiEndpoint endpoint : endpoints) {
                        sb.append("| ").append(endpoint.methodName).append(" | ")
                          .append(endpoint.path).append(" | ")
                          .append(endpoint.httpMethod).append(" | ")
                          .append(endpoint.consumes == null ? "" : endpoint.consumes).append(" | ")
                          .append(endpoint.produces == null ? "" : endpoint.produces).append(" |\n");
                    }
                    
                    sb.append("\n");
                }
            }
            
            sb.append("## Method Details\n\n");
            
            for (MethodInfo method : classInfo.methods) {
                sb.append("### `").append(method.signature).append("`\n\n");
                
                if (!method.javadoc.isEmpty()) {
                    sb.append("**Documentation:**\n\n");
                    sb.append(formatJavadoc(method.javadoc)).append("\n\n");
                }
                
                if (!method.annotations.isEmpty()) {
                    sb.append("**Annotations:**\n\n");
                    for (String annotation : method.annotations) {
                        sb.append("- `").append(annotation).append("`\n");
                    }
                    sb.append("\n");
                }
            }
            
            String fileName = packageName.replace(".", "_") + "_" + className + ".md";
            writeToFile(METHOD_DETAILS_DIR + "/" + fileName, sb.toString());
        }
    }
    
    private static String formatJavadoc(String javadoc) {
        return javadoc.replaceAll("\\*\\s+", "")
                     .replaceAll("\\s*@param\\s+([^\\s]+)\\s+", "**Parameter `$1`**: ")
                     .replaceAll("\\s*@return\\s+", "**Returns**: ")
                     .replaceAll("\\s*@throws\\s+([^\\s]+)\\s+", "**Throws `$1`**: ");
    }
    
    private static String simplifyClassName(String fullClassName) {
        return fullClassName.contains(".") ? 
               fullClassName.substring(fullClassName.lastIndexOf(".") + 1) : 
               fullClassName;
    }
    
    private static void createOutputDirectories() {
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        
        File methodDir = new File(OUTPUT_DIR, METHOD_DETAILS_DIR);
        if (!methodDir.exists()) {
            methodDir.mkdir();
        }
    }
    
    private static void writeToFile(String fileName, String content) throws IOException {
        File file = new File(OUTPUT_DIR, fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
    
    static class ClassInfo {
        String name;
        String packageName;
        String filePath;
        String superClass;
        List<String> interfaces = new ArrayList<>();
        List<String> subClasses = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();
        boolean isInterface;
        boolean isAbstract;
        boolean isRestResource;
    }
    
    static class MethodInfo {
        String name;
        String signature;
        String javadoc;
        List<String> annotations = new ArrayList<>();
    }
    
    static class ApiEndpoint {
        String httpMethod;
        String path;
        String methodName;
        String className;
        String packageName;
        String consumes;
        String produces;
    }
}
