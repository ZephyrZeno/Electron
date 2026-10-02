package top.lacrus.zephyrzeno.electron.logic;

public enum LogicFunction {
    AND, OR, NOT, XOR, NAND, NOR, XNOR, BUFFER;

    public boolean apply(boolean a, boolean b) {
        return switch (this) {
            case AND -> a && b;
            case OR -> a || b;
            case NOT -> !a;
            case XOR -> a ^ b;
            case NAND -> !(a && b);
            case NOR -> !(a || b);
            case XNOR -> a == b;
            case BUFFER -> a;
        };
    }

    public boolean unary() { return this == NOT || this == BUFFER; }
}
