package model;

import java.util.List;

public abstract class Stmt {
    public interface Visitor<R> {
        R visitExpressionStmt(Expression stmt);
        R visitFunctionStmt(Function stmt);
        R visitReturnStmt(Return stmt);
        R visitPrintStmt(Print stmt);
        R visitVarStmt(Var stmt);
        R visitBlockStmt(Block stmt);
        R visitClassStmt(Class stmt);
        R visitReplStmt(Repl stmt);
        R visitIfStmt(If stmt);
        R visitWhileStmt(While stmt);
        R visitLogicStmt(Logic stmt);
    }
    public static class Expression extends Stmt {
        public Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }

        public final Expr expression;
    }
    public static class Function extends Stmt {
        public Function(Token name, List<Token> params, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }

        public final Token name;
        public final List<Token> params;
        public final List<Stmt> body;
    }
    public static class Return extends Stmt {
        public Return(Token keyword, Expr value) {
            this.keyword = keyword;
            this.value = value;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }

        public final Token keyword;
        public final Expr value;
    }
    public static class Print extends Stmt {
        public Print(Expr expression) {
            this.expression = expression;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }

        public final Expr expression;
    }
    public static class Var extends Stmt {
        public Var(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }

        public final Token name;
        public final Expr initializer;
    }
    public static class Block extends Stmt {
        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }

        public final List<Stmt> statements;
    }
    public static class Class extends Stmt {
        public Class(Token name, Expr.Variable superclass, List<Stmt.Function> statics, List<Stmt.Function> getters, List<Stmt.Function> methods) {
            this.name = name;
            this.superclass = superclass;
            this.statics = statics;
            this.getters = getters;
            this.methods = methods;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitClassStmt(this);
        }

        public final Token name;
        public final Expr.Variable superclass;
        public final List<Stmt.Function> statics;
        public final List<Stmt.Function> getters;
        public final List<Stmt.Function> methods;
    }
    public static class Repl extends Stmt {
        public Repl(Expr expression) {
            this.expression = expression;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitReplStmt(this);
        }

        public final Expr expression;
    }
    public static class If extends Stmt {
        public If(Expr cond, Stmt thenBranch, Stmt elseBranch) {
            this.cond = cond;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }

        public final Expr cond;
        public final Stmt thenBranch;
        public final Stmt elseBranch;
    }
    public static class While extends Stmt {
        public While(Expr cond, Stmt loop, Stmt inc) {
            this.cond = cond;
            this.loop = loop;
            this.inc = inc;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }

        public final Expr cond;
        public final Stmt loop;
        public final Stmt inc;
    }
    public static class Logic extends Stmt {
        public Logic(Token name) {
            this.name = name;
        }

        @Override
        public<R> R accept(Visitor<R> visitor) {
            return visitor.visitLogicStmt(this);
        }

        public final Token name;
    }

    public abstract <R> R accept(Visitor<R> visitor);
}
