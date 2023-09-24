package lox;

import constant.VariableValue;
import exceptions.InterpreterError;
import exceptions.RuntimeError;
import model.Token;

import java.util.HashMap;
import java.util.Map;

public class Environment {

    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public String toString() {
        String ret = values.toString();
        if (enclosing != null) {
            ret += enclosing.toString();
        }
        return ret;
    }

    public Environment() {
        enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    public Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);
            if (VariableValue.UNINIT.equals(value)) {
                throw new RuntimeError(name,
                        "Uninitialized variable '" + name.lexeme + "'.");
            }
            return values.get(name.lexeme);
        }

        if (enclosing != null)
            return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i ++) {
            if (environment == null) {
                throw new InterpreterError("Environment is empty!  This is an interpreter bug.");
            }
            environment = environment.enclosing;
        }
        return environment;
    }

    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    public void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

}
