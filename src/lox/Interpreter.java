package lox;

import constant.ClassConstant;
import constant.FunctionType;
import constant.LoopFlag;
import constant.VariableValue;
import exceptions.InterpreterError;
import exceptions.Return;
import exceptions.RuntimeError;
import model.Expr;
import model.Stmt;
import model.Token;
import model.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Expr.Visitor<Object>,
                                    Stmt.Visitor<Void> {

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    private LoopFlag loopFlag = LoopFlag.NONE;


    public Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if (Math.abs((double)right) < 1e-10) {
                    throw new RuntimeError(expr.operator,
                            "You cannot divide a number by zero.");
                }
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or at least one string.");
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL:
                return !isEquals(left, right);
            case EQUAL_EQUAL:
                return isEquals(left, right);
            case COMMA:
                return right;
        }
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument: expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }
        LoxCallable func = (LoxCallable)callee;
        if (arguments.size() != func.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    func.arity() + " arguments but got " +
                    arguments.size() + ".");
        }
        return func.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            Object value = ((LoxInstance) object).get(expr.name);
            if (value instanceof LoxFunction && ((LoxFunction) value).functionType == FunctionType.GETTER) {
                value = ((LoxFunction) value).call(this, new ArrayList<>());
            }
            return value;
        }

        throw new RuntimeError(expr.name,
                "Only instances have properties.");
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, ClassConstant.SUPER);
        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, ClassConstant.THIS);
        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        return method.bind(object);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name,
                    "Only instances have fields.");
        }
        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);

        return value;
    }

    @Override
    public Object visitLambdaExpr(Expr.Lambda expr) {
        if (expr.body.size() == 0) {
            throw new RuntimeError(expr.name, "It's not allowed to define a lambda function without" +
                    " any statements.");
        }
        Stmt.Function lambdaFunction = new Stmt.Function(
                expr.name,
                expr.params,
                expr.body);
        return new LoxFunction(lambdaFunction, environment, FunctionType.LAMBDA);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expr);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case PLUS:
                return right;
            case MINUS:
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }
        return null;
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        Object cond = evaluate(expr.cond);
        if (isTruthy(cond)) {
            return evaluate(expr.left);
        } else {
            return evaluate(expr.right);
        }
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);

        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);
        if (TokenType.AND.equals(expr.name.type)) {
            if (!isTruthy(left))
                return left;
        } else {
            if (isTruthy(left))
                return left;
        }
        return evaluate(expr.right);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private void checkNumberOperands(Token operator, Object... values) {
        int isNumberCnt = 0;
        for (Object value: values) {
            if (value instanceof Double) {
                isNumberCnt++;
            }
        }
        if (isNumberCnt == values.length)
            return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private void execute(Stmt statement) {
        if (loopFlag != LoopFlag.NONE)
            return;
        statement.accept(this);
    }

    public  void executeBlock(List<Stmt> statements,
                              Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement: statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private String stringify(Object obj) {
        if (obj == null) return "nil";
        if (obj instanceof Double) {
            String text = obj.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return obj.toString();
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, FunctionType.FUNCTION);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);
        throw new Return(value);
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = VariableValue.UNINIT;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        Environment environment = new Environment(this.environment);
        executeBlock(stmt.statements, environment);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name,
                        "Superclass must be a class");
            }
        }
        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.define(ClassConstant.SUPER, superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method: stmt.methods) {
            FunctionType type = ClassConstant.INIT.equals(method.name.lexeme) ?
                    FunctionType.INITIALIZER : FunctionType.METHOD;
            LoxFunction function = new LoxFunction(method, environment, type);
            methods.put(method.name.lexeme, function);
        }
        for (Stmt.Function method: stmt.statics) {
            FunctionType type = FunctionType.CLASS_STATIC;
            LoxFunction function = new LoxFunction(method, environment, type);
            methods.put(method.name.lexeme, function);
        }
        for (Stmt.Function method: stmt.getters) {
            FunctionType type = FunctionType.GETTER;
            LoxFunction function = new LoxFunction(method, environment, type);
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);

        if (superclass != null) {
            environment = environment.enclosing;
        }

        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitReplStmt(Stmt.Repl stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.cond))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.cond))) {
            execute(stmt.loop);
            if (loopFlag == LoopFlag.BREAK) {
                loopFlag = LoopFlag.NONE;
                break;
            } else if (loopFlag == LoopFlag.CONTINUE) {
                loopFlag = LoopFlag.NONE;
            }
            if (stmt.inc != null)
                execute(stmt.inc);
        }
        return null;
    }

    @Override
    public Void visitLogicStmt(Stmt.Logic stmt) {
        if (loopFlag == LoopFlag.NONE) {
            throw new InterpreterError("This is an interpreter bug!!");
        }
        switch (stmt.name.type) {
            case BREAK:
                loopFlag = LoopFlag.BREAK;
                break;
            case CONTINUE:
                loopFlag = LoopFlag.CONTINUE;
                break;
        }
        return null;
    }

}
