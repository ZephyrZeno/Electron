package top.lacrus.zephyrzeno.electron.circuit;

import net.minecraft.text.Text;

/** Preserve translation keys and arguments until the receiving client chooses its language. */
public final class CircuitError extends IllegalArgumentException {
    private final Object[] arguments;
    public CircuitError(String key,Object... arguments){super(key);this.arguments=arguments.clone();}
    public Text text(){return Text.translatable(getMessage(),arguments);}
    public static Text text(Throwable error){
        if(error instanceof CircuitError circuit)return circuit.text();
        return Text.translatable(error.getMessage()!=null&&error.getMessage().startsWith("error.electron.")?error.getMessage():"error.electron.generic");
    }
}
