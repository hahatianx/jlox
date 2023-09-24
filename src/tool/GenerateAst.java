package tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {

    public static void main(String[] args) throws IOException {
        // if (args.length != 1) {
        //     System.err.println("Usage: generate_ast <output directory>");
        //     System.exit(64);
        // }

        // String outputDir = args[0];
        String outputDir = "./src/model";
        defineAst(outputDir, "Expr", Arrays.asList(
                "Binary     : Expr left, Token operator, Expr right",
                "Call       : Expr callee, Token paren, List<Expr> arguments",
                "Get        : Expr object, Token name",
                "This       : Token keyword",
                "Super      : Token keyword, Token method",
                "Set        : Expr object, Token name, Expr value",
                "Lambda     : Token name, List<Token> params, List<Stmt> body",
                "Grouping   : Expr expr",
                "Literal    : Object value",
                "Unary      : Token operator, Expr right",
                "Ternary    : Expr cond, Expr left, Expr right",
                "Variable   : Token name",
                "Assign     : Token name, Expr value",
                "Logical    : Token name, Expr left, Expr right"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
                "Expression : Expr expression",
                "Function   : Token name, List<Token> params, List<Stmt> body",
                "Return     : Token keyword, Expr value",
                "Print      : Expr expression",
                "Var        : Token name, Expr initializer",
                "Block      : List<Stmt> statements",
                "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> statics, List<Stmt.Function> getters, List<Stmt.Function> methods",
                "Repl       : Expr expression",
                "If         : Expr cond, Stmt thenBranch, Stmt elseBranch",
                "While      : Expr cond, Stmt loop, Stmt inc",
                "Logic      : Token name"
        ));
    }

    private static void defineAst(
            String outputDir, String baseName, List<String> types)
        throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package model;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("public abstract class "  + baseName + " {");

        defineVisitor(writer, baseName, types);

        for (String type: types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.println();
        writer.println("    public abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.close();
    }

    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        writer.println("    public static class " + className + " extends " + baseName + " {");

        writer.println("        public " + className + "(" + fieldList + ") {");

        String[] fields = fieldList.split(", ");
        for (String field: fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }


        writer.println("        }");

        writer.println();
        writer.println("        @Override");
        writer.println("        public<R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" +
                className + baseName + "(this);");
        writer.println("        }");

        writer.println();
        for (String field: fields) {
            writer.println("        public final " + field + ";");
        }

        writer.println("    }");
    }

    private static void defineVisitor(
            PrintWriter writer, String baseName, List<String> types) {
        writer.println("    public interface Visitor<R> {");

        for (String type: types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" +
                    typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("    }");
    }

}
