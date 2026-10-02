package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.io.*;
import java.util.concurrent.*;

/** Expensive structure compilation stays off the Minecraft server thread. */
public final class CalculatorCompiler {
    private static ExecutorService executor;
    private static Future<CompactBlueprint> future;
    private static volatile CalculatorStartup startup;
    private static volatile CompactBlueprint legacy;
    private static volatile String progress="尚未开始";
    private CalculatorCompiler() {}

    public static synchronized Future<CompactBlueprint> prepare() {
        if(future!=null)return future;
        executor=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"Electron circuit compiler");t.setDaemon(true);return t;});
        future=executor.submit(()->{
            progress="载入电路蓝图";
            try(InputStream in=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator.ebp")) {
                if(in==null)throw new IOException("缺少按钮计算器蓝图，请重新构建发行包");
                CompactBlueprint plan=CompactBlueprint.read(in);
                if(plan.inputs.size()!=KeypadCircuit.KEYS.size()||!plan.inputs.containsKey("KEY_RUN"))
                    throw new IOException("按钮计算器蓝图接口不完整");
                if(!Boolean.getBoolean("electron.captureStartup"))try(InputStream state=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator.startup")) {
                    if(state==null)throw new IOException("缺少计算器初始状态，请重新构建发行包");
                    startup=CalculatorStartup.read(state,plan);
                }
                try(InputStream old=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator-legacy.ebp")) {
                    if(old!=null)legacy=CompactBlueprint.read(old);
                }
                progress="蓝图就绪："+plan.blocks.size()+" 个方块";return plan;
            }catch(Exception error){progress="规划失败："+error.getMessage();throw error;}
        });
        return future;
    }
    public static String progress(){return progress;}
    public static CalculatorStartup startup(){return startup;}
    public static CompactBlueprint legacy(){return legacy;}
    public static synchronized void stop(){if(executor!=null)executor.shutdownNow();executor=null;future=null;startup=null;legacy=null;progress="尚未开始";}
}
