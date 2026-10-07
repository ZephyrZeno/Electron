package top.lacrus.zephyrzeno.electron.power;

/** Per-machine warning cadence with thermal hysteresis; never a global tick-modulo siren. */
public final class PlantAlarm {
    public enum Cue { NONE, WARNING, URGENT }
    private long last=Long.MIN_VALUE/2;
    private boolean hot;
    private Cue previous=Cue.NONE;
    public Cue update(long tick,boolean intact,boolean cooling,double temperature){
        hot=intact&&!cooling&&(hot?temperature>38:temperature>=45);
        Cue severity=!hot?Cue.NONE:temperature>=100?Cue.URGENT:Cue.WARNING;
        if(severity==Cue.NONE){previous=severity;return Cue.NONE;}
        long interval=severity==Cue.URGENT?100:400;
        boolean escalation=severity==Cue.URGENT&&previous!=Cue.URGENT;
        previous=severity;
        if(tick-last<(escalation?40:interval))return Cue.NONE;
        last=tick;return severity;
    }
}
