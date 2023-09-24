package lox;

import constant.ClassConstant;
import constant.FunctionType;
import exceptions.Return;
import model.Stmt;

import java.util.List;

public class LoxFunction implements LoxCallable {

    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isLambda;
    private final boolean isInitializer;

    public final FunctionType functionType;

    public LoxFunction(Stmt.Function declaration, Environment closure, FunctionType functionType) {
        this.declaration = declaration;
        this.closure = closure;
        this.functionType = functionType;
        this.isInitializer = FunctionType.INITIALIZER.equals(this.functionType);
        this.isLambda = FunctionType.LAMBDA.equals(this.functionType);
    }

    public LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define(ClassConstant.THIS, instance);
        return new LoxFunction(this.declaration, environment, this.functionType);
    }

    @Override
    public int arity() {
        return this.declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(this.closure);
        for (int i = 0; i < this.declaration.params.size(); i++) {
            environment.define(this.declaration.params.get(i).lexeme, arguments.get(i));
        }
        try {
            interpreter.executeBlock(this.declaration.body, environment);
        } catch (Return ret) {
            if (isInitializer)
                return closure.getAt(0, ClassConstant.THIS);
            return ret.getValue();
        }
        if (isInitializer)
            return closure.getAt(0, ClassConstant.THIS);
        return null;
    }

    @Override
    public String toString() {
        if (isLambda) {
            return "<fn lambda function>";
        } else {
            return "<fn " + declaration.name.lexeme + ">";
        }
    }
}
