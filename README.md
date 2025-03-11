# CodeBase-Analyzer-without-AI

This Java-based utility will help you understand and navigate your codebase without relying on external AI tools. Here's what it does:
Key Features:

API Mapping: Detects Jersey REST endpoints, showing HTTP methods, paths, and content types
Class Hierarchy: Visualizes inheritance and implementation relationships
Method Documentation: Extracts method signatures, JavaDoc, and annotations
Enum Summary: Lists all enums and their values
Package Organization: Shows how classes are organized by package

How to Use:

Copy this file into your repository (e.g., as CodebaseAnalyzer.java)
Compile it: javac CodebaseAnalyzer.java
Run it against your source directory: java CodebaseAnalyzer /path/to/your/src
For specific class analysis: java CodebaseAnalyzer /path/to/your/src YourClassName

Output Files:

code-analysis/api-summary.md: All REST API endpoints
code-analysis/class-hierarchy.md: Class inheritance tree and package summary
code-analysis/enum-summary.md: All enum types and values
code-analysis/method-details/: Detailed documentation for each class's methods

The reports are generated in Markdown format, which can be easily viewed in most text editors or IDEs, or converted to HTML if needed.
