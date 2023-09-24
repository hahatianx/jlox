package lox;

import constant.ClassConstant;
import constant.ClassType;
import constant.FunctionType;
import model.Expr;
import model.Stmt;
import model.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();

    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    public Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private void resolve(Stmt stmt) {
        if (stmt == null) return;
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        if (expr == null) return;
        expr.accept(this);
    }

    void resolve(List<Stmt> statements) {
        statements.forEach(this::resolve);
    }

    void declare(Token token) {
        if (scopes.empty()) return;
        Map<String, Boolean> scope = scopes.peek();

        if (scope.containsKey(token.lexeme)) {
            Lox.error(token, "Already a variable with this name in this scope.");
        }

        scope.put(token.lexeme, false);
    }

    void define(Token token) {
        if (scopes.empty()) return;
        scopes.peek().put(token.lexeme, true);
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; --i) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - i - 1);
                return;
            }
        }
    }

    private void resolveFunction(Stmt.Function stmt, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param: stmt.params) {
            declare(param);
            define(param);
        }
        resolve(stmt.body);

        if (FunctionType.GETTER.equals(type)) {
            boolean hasReturn = false;
            for (Stmt statement : stmt.body) {
                hasReturn |= (statement instanceof Stmt.Return);
            }

            if (!hasReturn) {
                Lox.error(stmt.name, "A class getter must have a return statement.");
            }
        }

        endScope();

        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument: expr.arguments)
            resolve(argument);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
        }
        if (currentFunction == FunctionType.CLASS_STATIC) {
            Lox.error(expr.keyword,
                    "Can't use 'this' in a static method.");
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword,
                    "Can't use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitLambdaExpr(Expr.Lambda expr) {
        beginScope();
        for (Token param: expr.params) {
            declare(param);
            define(param);
        }
        resolve(expr.body);
        endScope();
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expr);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.cond);
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.empty() &&
        Boolean.FALSE.equals(scopes.peek().get(expr.name.lexeme))) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if (stmt.superclass != null &&
            stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name,
                    "A class cannot inherit from itself.");
        }

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put(ClassConstant.SUPER, true);
        }

        beginScope();

        for (Stmt.Function method: stmt.statics) {
            FunctionType declaration = FunctionType.CLASS_STATIC;

            if (ClassConstant.INIT.equals(method.name.lexeme)) {
                Lox.error(method.name,
                        "The name of a static method can't be 'init'.");
            }

            resolveFunction(method, declaration);
        }

        scopes.peek().put(ClassConstant.THIS, true);

        for (Stmt.Function method: stmt.getters) {
            FunctionType declaration = FunctionType.GETTER;

            if (ClassConstant.INIT.equals(method.name.lexeme)) {
                Lox.error(method.name,
                        "The name of a class getter can't be 'init'.");
            }

            resolveFunction(method, declaration);
        }

        for (Stmt.Function method: stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;

            if (ClassConstant.INIT.equals(method.name.lexeme)) {
                declaration = FunctionType.INITIALIZER;
            }

            resolveFunction(method, declaration);
        }


        endScope();

        if (stmt.superclass != null) {
            endScope();
        }

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitReplStmt(Stmt.Repl stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.cond);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.cond);
        resolve(stmt.loop);
        resolve(stmt.inc);
        return null;
    }

    @Override
    public Void visitLogicStmt(Stmt.Logic stmt) {
        return null;
    }
}
