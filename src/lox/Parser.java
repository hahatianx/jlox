package lox;

import constant.FunctionType;
import constant.ParserParallelFlag;
import exceptions.ParseError;
import model.Expr;
import model.Stmt;
import model.Token;
import model.TokenType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private int nestLoop = 0;
    private ParserParallelFlag parserParallelFlag = ParserParallelFlag.ALLOW;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peek2() {
        return tokens.get(current + 1);
    }

    private boolean forwardExist(int pace) {
        return pace + current < tokens.size();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;

    }

    private boolean match(TokenType... types) {
        for(TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Expr expression() {
        if (match(TokenType.FUN)) {
            return lambda();
        }
        return parallel();
    }

    private Expr.Lambda lambda() {
        consume(TokenType.LEFT_PAREN, "Expect '(' in lambda expression.");
        List<Token> parameters = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            ParserParallelFlag previous = parserParallelFlag;
            parserParallelFlag = ParserParallelFlag.DENY;
            do {
                if (parameters.size() >= 255) {
                    throw error(peek(), "Can't have more than 255 parameters.");
                }
                parameters.add(
                        consume(TokenType.IDENTIFIER, "Expect parameter name."));
            } while (match(TokenType.COMMA));
            parserParallelFlag = previous;
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        Token lambdaStart = consume(TokenType.LEFT_BRACE, "Expect '{' before lambda function body.");
        List<Stmt> body = block();
        return new Expr.Lambda(lambdaStart, parameters, body);
    }

    private Expr parallel() {
        Expr expr = choice();
        boolean allowParallel = ParserParallelFlag.ALLOW.equals(parserParallelFlag);
        while (allowParallel && match(TokenType.COMMA)) {
            Token operator = previous();
            Expr right = choice();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr choice() {
        Expr expr = assignment();
        if (match(TokenType.QUESTION_MARK)) {
            Expr left = assignment();
            if (match(TokenType.COLON)) {
                Expr right = assignment();
                expr = new Expr.Ternary(expr, left, right);
            }
        }
        return expr;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            throw error(equals, "Invalid assignment target.");
        }
         return expr;
    }

    private Expr or() {
        Expr expr = and();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(operator, expr, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(operator, expr, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.BANG, TokenType.PLUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER,
                        "Expect property name after '.'.");
                expr =  new Expr.Get(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr primary() {
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.NIL))  return new Expr.Literal(null);

        if (match(TokenType.THIS)) return new Expr.This(previous());

        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(TokenType.SUPER)) {
            Token keyword = previous();
            consume(TokenType.DOT, "Expect '.' after 'super'.");
            Token method = consume(TokenType.IDENTIFIER,
                    "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            ParserParallelFlag previous = parserParallelFlag;
            parserParallelFlag = ParserParallelFlag.DENY;
            do {
                if (arguments.size() >= 255) {
                    throw error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(TokenType.COMMA));
            parserParallelFlag = previous;
        }
        Token paren = consume(TokenType.RIGHT_PAREN,
                "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();

        if (match(TokenType.SEMICOLON)) {
            return new Stmt.Expression(expr);
        }
        return new Stmt.Repl(expr);
    }

    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr cond = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(cond, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        Expr cond = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        nestLoop++;
        Stmt loop = statement();
        nestLoop--;
        return new Stmt.While(cond, loop, null);
    }

    private Stmt forStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt init;
        if (match(TokenType.SEMICOLON)) {
            init = null;
        } else if (match(TokenType.VAR)) {
            init = varDeclaration();
        } else {
            init = expressionStatement();
        }

        Expr cond = null;
        if (!check(TokenType.SEMICOLON)) {
            cond = expression();
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

        Expr inc = null;
        if (!check(TokenType.RIGHT_PAREN)) {
            inc = expression();
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
        nestLoop++;
        Stmt loop = statement();
        nestLoop--;

        if (cond == null)
            cond = new Expr.Literal(true);

        loop = new Stmt.While(cond, loop, new Stmt.Expression(inc));

        if (init != null) {
            loop = new Stmt.Block(Arrays.asList(init, loop));
        }

        return loop;
    }

    private Stmt logicStatement() {
        Token token = previous();
        if (nestLoop == 0) {
            throw error(token, "Cannot use it outside a loop.");
        }
        Stmt statement = new Stmt.Logic(token);
        consume(TokenType.SEMICOLON, "Expect ';' after break or continue.");
        return statement;
    }

    private Stmt statement() {
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
        if (match(TokenType.BREAK)) return logicStatement();
        if (match(TokenType.CONTINUE)) return logicStatement();
        return expressionStatement();
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(TokenType.CLASS)) return classDeclaration();
            if (match(TokenType.FUN)) return function(FunctionType.FUNCTION);
            if (match(TokenType.VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // kind == Getter
    //  return a getter if '()' is not found after the name token, otherwise return normal class methods
    private Stmt.Function function(FunctionType kind) {
        Token name = consume(TokenType.IDENTIFIER, "Expect " + kind.name().toLowerCase() + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!FunctionType.GETTER.equals(kind) || check(TokenType.LEFT_PAREN)) {
            kind = FunctionType.METHOD;
            consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind.name().toLowerCase() + " name.");
            if (!check(TokenType.RIGHT_PAREN)) {
                ParserParallelFlag previous = parserParallelFlag;
                parserParallelFlag = ParserParallelFlag.DENY;
                do {
                    if (parameters.size() >= 255) {
                        throw error(peek(), "Can't have more than 255 parameters.");
                    }
                    parameters.add(
                            consume(TokenType.IDENTIFIER, "Expect parameter name."));
                } while (match(TokenType.COMMA));
                parserParallelFlag = previous;
            }
            consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        }
        consume(TokenType.LEFT_BRACE, "Expect '{' before " + kind.name().toLowerCase() + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = expression();
        }
        consume(TokenType.SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt classDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(TokenType.LESS)) {
            consume(TokenType.IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }
        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> statics = new ArrayList<>();
        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> getters = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            if (TokenType.CLASS.equals(peek().type)) {
                // Static method
                advance();
                statics.add(function(FunctionType.METHOD));
            } else if (forwardExist(1) && TokenType.LEFT_PAREN.equals(peek2().type)) {
                // name()  -> normal methods
                methods.add(function(FunctionType.METHOD));
            } else {
                // getter
                getters.add(function(FunctionType.GETTER));
            }
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, superclass, statics, getters, methods);
    }

    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(TokenType.EQUAL)) {
            initializer = expression();
        }

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;

            switch(peek().type) {
                case CLASS:
                case FOR:
                case FUN:
                case IF:
                case PRINT:
                case RETURN:
                case VAR:
                case WHILE:
                    return;
            }

            advance();
        }
    }

}
