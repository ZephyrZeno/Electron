package top.lacrus.zephyrzeno.electron.logic;

/** Electrical gates expose 0, 1, X, Z; unconnected or indeterminate inputs are X. */
public final class WireLogic {
    public static int input(double v,double supply,boolean driven){if(!driven||supply<.5||v<-.3||v>supply+.3)return 2;return v<=.3*supply?0:v>=.7*supply?1:2;}
    public static int evaluate(LogicFunction function,int a,int b) {
        if(function.unary())return a>1?2:function.apply(a==1,false)?1:0;
        if(function==LogicFunction.AND||function==LogicFunction.NAND){if(a==0||b==0)return function==LogicFunction.AND?0:1;}
        if(function==LogicFunction.OR||function==LogicFunction.NOR){if(a==1||b==1)return function==LogicFunction.OR?1:0;}
        if(a>1||b>1)return 2;return function.apply(a==1,b==1)?1:0;
    }
}
