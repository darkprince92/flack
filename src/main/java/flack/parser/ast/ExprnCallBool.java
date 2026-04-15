package flack.parser.ast;

import edu.mit.csail.sdg.alloy4compiler.ast.ExprCall;
import flack.parser.visitor.GenericVisitor;
import flack.parser.visitor.VoidVisitor;

public class ExprnCallBool extends ExprnCall {

    public ExprnCallBool(Node parent, ExprCall expr) {
        super(parent, expr);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public <R, V> R accept(GenericVisitor<R, V> visitor, V arg) {
        return visitor.visit(this, arg);
    }

    @Override
    public void accept(VoidVisitor visitor) {
        visitor.visit(this);
    }
}
