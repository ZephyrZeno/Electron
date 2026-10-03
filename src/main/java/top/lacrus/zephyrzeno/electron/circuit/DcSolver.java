package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;

/** Quasi-static DC model: finite source resistance and piecewise-linear LEDs.
 * Voltages are relative to one source return in each connected island. */
public final class DcSolver {
    public static final int MAX_ISLAND_NODES=4096;
    public record Branch(String id,String a,String b,double resistance,double emf,boolean led,double minCurrent,double maxCurrent) {
        public Branch(String id,String a,String b,double resistance,double emf,boolean led){this(id,a,b,resistance,emf,led,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY);}
        public Branch {
            if(!Double.isFinite(resistance)||resistance<=0||!Double.isFinite(emf)||Double.isNaN(minCurrent)||Double.isNaN(maxCurrent)||minCurrent>maxCurrent)throw new IllegalArgumentException("Invalid component values");
        }
        public static Branch resistor(String id,String a,String b,double r){return new Branch(id,a,b,r,0,false);}
        public static Branch source(String id,String plus,String minus,double volts,double r){return new Branch(id,plus,minus,r,volts,false);}
        public static Branch led(String id,String anode,String cathode){return new Branch(id,anode,cathode,20,2,true);}
        public static Branch diode(String id,String anode,String cathode){return new Branch(id,anode,cathode,2,.7,true);}
    }
    public record Result(Map<String,Double> voltage,Map<String,Double> current,Map<String,String> reference) {
        public double current(String id){return current.getOrDefault(id,0d);}
        public double voltage(String node){return voltage.getOrDefault(node,0d);}
        public boolean commonReference(String a,String b){return reference.containsKey(a)&&Objects.equals(reference.get(a),reference.get(b));}
    }
    public static Result solve(List<Branch> branches) {
        Map<String,String> parent=new HashMap<>();
        for(var e:branches){parent.putIfAbsent(e.a,e.a);parent.putIfAbsent(e.b,e.b);parent.put(find(parent,e.a),find(parent,e.b));}
        Map<String,List<Branch>> islands=new LinkedHashMap<>();
        for(var e:branches)islands.computeIfAbsent(find(parent,e.a),k->new ArrayList<>()).add(e);
        Map<String,Double> volts=new HashMap<>(),amps=new HashMap<>();Map<String,String> refs=new HashMap<>();
        for(var island:islands.values())solveIsland(island,volts,amps,refs);
        return new Result(Map.copyOf(volts),Map.copyOf(amps),Map.copyOf(refs));
    }
    private static String find(Map<String,String> parent,String node){String root=node;while(!parent.get(root).equals(root))root=parent.get(root);while(!node.equals(root)){String next=parent.put(node,root);node=next;}return root;}
    private static void solveIsland(List<Branch> edges,Map<String,Double> volts,Map<String,Double> amps,Map<String,String> refs) {
        SortedSet<String> nodes=new TreeSet<>();for(var e:edges){nodes.add(e.a);nodes.add(e.b);}
        if(nodes.size()>MAX_ISLAND_NODES)throw new IllegalStateException("error.electron.solver_budget");
        String ground=edges.stream().filter(e->!e.led&&e.emf!=0).map(Branch::b).findFirst().orElse(nodes.first());
        Map<String,Integer> index=new HashMap<>();int n=0;for(String node:nodes){refs.put(node,ground);if(!node.equals(ground))index.put(node,n++);}
        Set<String> lit=new HashSet<>();Map<String,Integer> limits=new HashMap<>();
        for(int iteration=0;iteration<Math.min(512,edges.size()*2+40);iteration++) {
            double[][] matrix=n<=128?new double[n][n+1]:null;Sparse sparse=matrix==null?new Sparse(n):null;
            for(var e:edges) {
                var stamp=stamp(e,!e.led||lit.contains(e.id),limits.getOrDefault(e.id,0));double g=stamp[0],rhs=stamp[1];
                Integer a=index.get(e.a),b=index.get(e.b);
                if(sparse!=null)sparse.stamp(a,b,g,rhs);
                else {if(a!=null){matrix[a][a]+=g;matrix[a][n]+=rhs;}if(b!=null){matrix[b][b]+=g;matrix[b][n]-=rhs;}if(a!=null&&b!=null){matrix[a][b]-=g;matrix[b][a]-=g;}}
            }
            double[] solution=sparse==null?eliminate(matrix):sparse.solve();volts.put(ground,0d);index.forEach((key,value)->volts.put(key,solution[value]));
            boolean changed=false;
            for(var e:edges)if(e.led) {
                double delta=volts.get(e.a)-volts.get(e.b);
                boolean on=delta>e.emf+1e-8||(lit.contains(e.id)&&delta>=e.emf-1e-8);
                if(on!=lit.contains(e.id)){changed=true;if(on)lit.add(e.id);else lit.remove(e.id);}
            }
            String pivotId=null;int pivotMode=0;double worst=0;
            for(var e:edges){
                int previous=limits.getOrDefault(e.id,0),next=0;
                double trial=(volts.get(e.a)-volts.get(e.b)-e.emf)/e.resistance;
                if(!e.led||lit.contains(e.id)){
                    if(trial<e.minCurrent-1e-9||(previous<0&&trial<e.minCurrent+1e-9))next=-1;
                    else if(trial>e.maxCurrent+1e-9||(previous>0&&trial>e.maxCurrent-1e-9))next=1;
                }
                if(previous!=next){
                    int side=next!=0?next:previous;double boundary=side<0?e.minCurrent:e.maxCurrent;
                    double violation=Math.abs(trial-boundary)*e.resistance;
                    if(pivotId==null||violation>worst){pivotId=e.id;pivotMode=next;worst=violation;}
                }
            }
            // Pivot one limiter at a time. Clamping unequal series limits simultaneously is infeasible.
            if(!changed&&pivotId!=null){limits.put(pivotId,pivotMode);changed=true;}
            if(!changed) {
                for(var e:edges){var stamp=stamp(e,!e.led||lit.contains(e.id),limits.getOrDefault(e.id,0));amps.put(e.id,stamp[0]*(volts.get(e.a)-volts.get(e.b))-stamp[1]);}
                return;
            }
        }
        throw new IllegalStateException("error.electron.not_converged");
    }
    /** Norton stamp I = g(Va - Vb) - rhs. Tiny off-conductance keeps floating nodes solvable. */
    private static double[] stamp(Branch e,boolean active,int limit){
        if(!active)return new double[]{1e-9,0};
        if(limit==0)return new double[]{1/e.resistance,e.emf/e.resistance};
        double current=limit<0?e.minCurrent:e.maxCurrent,g=1e-12;
        return new double[]{g,g*(e.emf+e.resistance*current)-current};
    }
    private static double[] eliminate(double[][] a) {
        int n=a.length;
        for(int k=0;k<n;k++) {
            int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][k])>Math.abs(a[pivot][k]))pivot=i;
            if(Math.abs(a[pivot][k])<1e-14)throw new IllegalStateException("error.electron.no_reference");
            double[] row=a[k];a[k]=a[pivot];a[pivot]=row;
            for(int i=k+1;i<n;i++){double factor=a[i][k]/a[k][k];for(int j=k;j<=n;j++)a[i][j]-=factor*a[k][j];}
        }
        double[] x=new double[n];for(int i=n-1;i>=0;i--){double value=a[i][n];for(int j=i+1;j<n;j++)value-=a[i][j]*x[j];x[i]=value/a[i][i];if(!Double.isFinite(x[i]))throw new IllegalStateException("error.electron.invalid_value");}return x;
    }
    /** Symmetric conductance elimination with minimum-degree ordering; no dense N² allocation. */
    private static final class Sparse {
        final Map<Integer,Double>[] rows;final double[] diag,rhs;int fill;
        @SuppressWarnings("unchecked") Sparse(int n){rows=(Map<Integer,Double>[])new Map<?,?>[n];for(int i=0;i<n;i++)rows[i]=new HashMap<>();diag=new double[n];rhs=new double[n];}
        void add(int a,int b,double value){if(a==b){diag[a]+=value;return;}if(value==0)return;if(!rows[a].containsKey(b))fill++;rows[a].merge(b,value,Double::sum);if(fill>2_000_000)throw new IllegalStateException("error.electron.solver_budget");}
        void stamp(Integer a,Integer b,double g,double value){if(a!=null){diag[a]+=g;rhs[a]+=value;}if(b!=null){diag[b]+=g;rhs[b]-=value;}if(a!=null&&b!=null){add(a,b,-g);add(b,a,-g);}}
        double[] solve(){int n=rows.length;int[] order=new int[n];boolean[] done=new boolean[n];
            for(int k=0;k<n;k++){int pivot=-1;for(int i=0;i<n;i++)if(!done[i]&&(pivot<0||rows[i].size()<rows[pivot].size()))pivot=i;order[k]=pivot;done[pivot]=true;double d=diag[pivot];if(Math.abs(d)<1e-14)throw new IllegalStateException("error.electron.no_reference");var near=new ArrayList<>(rows[pivot].entrySet());
                for(int i=0;i<near.size();i++){int a=near.get(i).getKey();double g=near.get(i).getValue();rhs[a]-=g*rhs[pivot]/d;diag[a]-=g*g/d;rows[a].remove(pivot);fill--;for(int j=i+1;j<near.size();j++){int b=near.get(j).getKey();double value=-g*near.get(j).getValue()/d;add(a,b,value);add(b,a,value);}}
            }
            double[] values=new double[n];for(int k=n-1;k>=0;k--){int at=order[k];double v=rhs[at];for(var e:rows[at].entrySet())v-=e.getValue()*values[e.getKey()];values[at]=v/diag[at];if(!Double.isFinite(values[at]))throw new IllegalStateException("error.electron.invalid_value");}return values;
        }
    }
}
