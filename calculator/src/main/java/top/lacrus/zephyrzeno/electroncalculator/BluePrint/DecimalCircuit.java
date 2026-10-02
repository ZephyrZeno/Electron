package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;

/** Gate synthesis only. Minecraft torches and repeaters execute the resulting circuit. */
public final class DecimalCircuit {
    public enum Module { INPUT, CONTROL, CONVERT, ADD, SUBTRACT, MULTIPLY, DIVIDE, MEMORY, DISPLAY }
    public enum Kind { INPUT, NOR, REGISTER }
    public record Node(Kind kind, String name, int a, int b, Module module) {}
    public final List<Node> nodes = new ArrayList<>();
    public final Map<String, Integer> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
    public record Adder(int[] a,int[] b,int carryIn,int[] sum,int carryOut,Module module,int firstNode) {}
    public final List<Adder> adders=new ArrayList<>();
    public record LogicCell(String type,int[] inputs,int output,Module module) {}
    public final List<LogicCell> logicCells=new ArrayList<>();
    private int cell(String type,int a,int b,int result,int first) {
        if(result>=first)logicCells.add(new LogicCell(type,new int[]{a,b},result,module));return result;
    }
    private final Map<Long, Integer> common = new HashMap<>();
    private Module module = Module.CONTROL;
    private int zero, one;

    public static DecimalCircuit empty(Module module) {
        DecimalCircuit c=new DecimalCircuit();c.zero=-100;c.one=-101;
        c.zero=c.input("ZERO");c.one=c.not(c.zero);c.module=module;return c;
    }
    public void module(Module value){module=value;}
    public int reserveRegister(String name,int hold) {return register(name,zero,hold);}
    public void connectRegister(int id,int data) {Node old=nodes.get(id);nodes.set(id,new Node(Kind.REGISTER,old.name(),data,old.b(),old.module()));}
    public int low(){return zero;} public int high(){return one;}
    public void sevenSegment(String prefix,int[] nibble) {
        int[] patterns={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f},eq=new int[10];
        for(int value=0;value<10;value++){eq[value]=one;for(int bit=0;bit<4;bit++)eq[value]=and(eq[value],(value&(1<<bit))!=0?nibble[bit]:not(nibble[bit]));}
        for(int segment=0;segment<7;segment++){int lit=zero;for(int value=0;value<10;value++)if((patterns[value]&(1<<segment))!=0)lit=or(lit,eq[value]);outputs.put(prefix+segment,lit);}
    }

    public int input(String name) {
        int id = nodes.size(); nodes.add(new Node(Kind.INPUT, name, -1, -1, module)); inputs.put(name, id); return id;
    }
    public int nor(int a, int b) {
        if (a == one || b == one) return zero;
        if (one >= 0 && a == zero && b == zero) return one;
        if (a == zero && b != zero) a=b;
        if (b == zero && a != zero) b=a;
        if(a==b) {
            Node n=nodes.get(a);
            if(n.kind()==Kind.NOR && n.a()==n.b())return n.a();
        }
        final int left=a,right=b;
        long key = ((long)Math.min(a,b) << 32) | Math.max(a,b);
        return common.computeIfAbsent(key, ignored -> { int id=nodes.size(); nodes.add(new Node(Kind.NOR,null,left,right,module)); return id; });
    }
    public int not(int a) { return nor(a,a); }
    public int and(int a, int b) { if(a==b)return a; if(a==zero||b==zero)return zero; if(a==one)return b; if(b==one)return a; int first=nodes.size();return cell("AND",a,b,nor(not(a),not(b)),first); }
    public int or(int a, int b) { if(a==b)return a; if(a==zero)return b; if(b==zero)return a; int first=nodes.size();return cell("OR",a,b,not(nor(a,b)),first); }
    public int xor(int a, int b) {
        if(a==b)return zero; if(a==zero)return b; if(b==zero)return a; if(a==one)return not(b); if(b==one)return not(a);
        int first=nodes.size(),n=nor(a,b); return cell("XOR",a,b,not(nor(nor(a,n),nor(b,n))),first);
    }
    public int mux(int select, int no, int yes) { if(no==yes)return no; return nor(nor(no,select),nor(yes,not(select))); }
    public int[] mux(int s,int[] a,int[] b) { int[] r=new int[a.length]; for(int i=0;i<r.length;i++)r[i]=mux(s,a[i],b[i]); return r; }
    public int[] constant(int number,int bits) { int[] r=new int[bits]; for(int i=0;i<bits;i++)r[i]=(number&(1<<i))==0?zero:one; return r; }
    public int[] resize(int[] a,int bits) { int[] r=constant(0,bits); System.arraycopy(a,0,r,0,Math.min(a.length,bits)); return r; }
    public int[] shift(int[] a,int shift,int bits) { int[] r=constant(0,bits); for(int i=shift;i<bits&&i-shift<a.length;i++)r[i]=a[i-shift]; return r; }
    public record Sum(int[] bits,int carry) {}
    public Sum add(int[] a,int[] b,int carry) {
        int first=nodes.size(),carryIn=carry;
        int[] r=new int[a.length];
        for(int i=0;i<a.length;i++) {
            int neither=nor(a[i],b[i]);
            int equal=nor(nor(a[i],neither),nor(b[i],neither));
            int nc=nor(equal,carry);
            r[i]=nor(nor(equal,nc),nor(carry,nc));
            carry=nor(neither,nc);
        }
        adders.add(new Adder(a.clone(),b.clone(),carryIn,r.clone(),carry,module,first));
        return new Sum(r,carry);
    }
    public Sum subtract(int[] a,int[] b) { int[] inverted=Arrays.stream(b).map(this::not).toArray(); return add(a,inverted,one); }
    public int any(int[] a) { int r=zero; for(int bit:a)r=or(r,bit); return r; }
    public int[] decimal(int[] tens,int[] units) { return add(add(shift(tens,3,7),shift(tens,1,7),zero).bits(),resize(units,7),zero).bits(); }
    private int[] digitInput(String prefix) { int[] a=new int[4];for(int i=0;i<4;i++)a[i]=input(prefix+i);return a; }
    private int invalidDigit(int[] a) { return and(a[3],or(a[1],a[2])); }

    public static DecimalCircuit create() {
        DecimalCircuit c=new DecimalCircuit();
        // Dedicated constant source is placed as an unpowered wool block, not an editable lever.
        c.zero=-100; c.one=-101;
        c.zero=c.input("ZERO"); c.one=c.not(c.zero);
        c.module=Module.INPUT;
        int[] at=c.digitInput("AT"), au=c.digitInput("AU"), bt=c.digitInput("BT"), bu=c.digitInput("BU");
        c.module=Module.CONTROL;
        int op0=c.input("OP0"),op1=c.input("OP1"),hold=c.input("HOLD");
        int invalid=c.or(c.or(c.invalidDigit(at),c.invalidDigit(au)),c.or(c.invalidDigit(bt),c.invalidDigit(bu)));
        c.module=Module.CONVERT;
        int[] a=c.decimal(at,au),b=c.decimal(bt,bu);
        c.module=Module.ADD;
        int[] added=c.add(c.resize(a,8),c.resize(b,8),c.zero).bits();
        c.module=Module.SUBTRACT;
        Sum difference=c.subtract(a,b);
        int negative=c.not(difference.carry());
        int[] magnitude=c.mux(negative,difference.bits(),c.subtract(b,a).bits());
        c.module=Module.MULTIPLY;
        int[] product=c.constant(0,14);
        for(int bit=0;bit<7;bit++) {
            int[] partial=c.constant(0,14);
            for(int j=0;j<7;j++)partial[bit+j]=c.and(a[j],b[bit]);
            product=c.add(product,partial,c.zero).bits();
        }
        c.module=Module.DIVIDE;
        // Round A/B to two decimal places using floor((100*A + floor(B/2))/B).
        int[] hundredA=c.add(c.add(c.shift(a,6,14),c.shift(a,5,14),c.zero).bits(),c.shift(a,2,14),c.zero).bits();
        int[] halfB=c.constant(0,14);for(int i=0;i<6;i++)halfB[i]=b[i+1];
        int[] dividend=c.add(hundredA,halfB,c.zero).bits();
        int[] remainder=c.constant(0,8), quotient=c.constant(0,14),divisor=c.resize(b,8);
        for(int bit=13;bit>=0;bit--) {
            remainder=c.shift(remainder,1,8); remainder[0]=dividend[bit];
            Sum trial=c.subtract(remainder,divisor);
            quotient[bit]=trial.carry();
            remainder=c.mux(trial.carry(),remainder,trial.bits());
        }
        int divide=c.and(op0,op1);
        int divisionByZero=c.and(divide,c.not(c.any(b)));
        int error=c.or(invalid,divisionByZero);
        c.module=Module.CONTROL;
        int[] selected=c.mux(op1,c.mux(op0,c.resize(added,14),c.resize(magnitude,14)),c.mux(op0,product,c.resize(quotient,14)));
        int sign=c.and(c.and(op0,c.not(op1)),negative);
        c.module=Module.MEMORY;
        int[] result=new int[14];
        for(int i=0;i<14;i++) {
            int d=c.and(selected[i],c.not(error));
            int id=c.nodes.size(); c.nodes.add(new Node(Kind.REGISTER,"R"+i,d,hold,c.module)); result[i]=id;
            c.outputs.put("R"+i,id);
        }
        c.outputs.put("NEG",c.register("NEG",c.and(sign,c.not(error)),hold));
        c.outputs.put("ERROR",c.register("ERROR",error,hold));
        c.outputs.put("DOT",c.register("DOT",divide,hold));
        c.module=Module.DISPLAY;
        // Four BCD digits derived from the stored binary word (shift/add-three).
        int[] bcd=c.constant(0,16);
        for(int bit=13;bit>=0;bit--) {
            if(bit<13)for(int digit=0;digit<4;digit++) {
                int start=digit*4; int[] nibble=Arrays.copyOfRange(bcd,start,start+4);
                int ge5=c.or(nibble[3],c.and(nibble[2],c.or(nibble[1],nibble[0])));
                int[] adjusted=c.add(nibble,c.constant(3,4),c.zero).bits();
                System.arraycopy(c.mux(ge5,nibble,adjusted),0,bcd,start,4);
            }
            bcd=c.shift(bcd,1,16); bcd[0]=result[bit];
        }
        for(int i=0;i<16;i++)c.outputs.put("BCD"+i,bcd[i]);
        int[] segments={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f};
        for(int digit=0;digit<4;digit++) {
            int[] eq=new int[10];
            for(int value=0;value<10;value++) {
                eq[value]=c.one;
                for(int bit=0;bit<4;bit++)eq[value]=c.and(eq[value],(value&(1<<bit))!=0?bcd[digit*4+bit]:c.not(bcd[digit*4+bit]));
            }
            for(int segment=0;segment<7;segment++) {
                int lit=c.zero;for(int value=0;value<10;value++)if((segments[value]&(1<<segment))!=0)lit=c.or(lit,eq[value]);
                c.outputs.put("SEG"+digit+"_"+segment,lit);
            }
        }
        return c;
    }

    private int register(String name,int data,int hold) {
        int id=nodes.size();nodes.add(new Node(Kind.REGISTER,name,data,hold,Module.MEMORY));return id;
    }

    public static DecimalCircuit smoke() {
        DecimalCircuit c=new DecimalCircuit();c.zero=-100;c.one=-101;
        c.zero=c.input("ZERO");c.one=c.not(c.zero);
        int a=c.input("A"),b=c.input("B"),hold=c.input("HOLD");
        int both=c.and(a,b);
        c.outputs.put("R0",both);c.outputs.put("R1",c.xor(a,b));
        c.outputs.put("R2",c.register("SMOKE",both,hold));
        c.outputs.put("SEG0_0",both);return c;
    }

    /** Reference evaluation for tests only; the mod never uses this to operate a built calculator. */
    public Map<String,Boolean> evaluate(Map<String,Boolean> values) {
        boolean[] signals=new boolean[nodes.size()];
        for(int i=0;i<signals.length;i++) {
            Node n=nodes.get(i);
            signals[i]=switch(n.kind()) {
                case INPUT -> n.name().equals("ZERO")?false:Objects.requireNonNull(values.get(n.name()),n.name());
                case NOR -> !(signals[n.a()]||signals[n.b()]);
                case REGISTER -> signals[n.a()];
            };
        }
        Map<String,Boolean> out=new LinkedHashMap<>(); outputs.forEach((name,id)->out.put(name,signals[id]));return out;
    }
}
