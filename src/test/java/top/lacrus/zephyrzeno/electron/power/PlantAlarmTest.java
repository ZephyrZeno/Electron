package top.lacrus.zephyrzeno.electron.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.lacrus.zephyrzeno.electron.power.PlantAlarm.Cue.*;

class PlantAlarmTest {
    @Test void emptyColdPlantNeverAlarmsAndFaultedPlantStaysSilent(){
        var alarm=new PlantAlarm();
        for(int t=0;t<1000;t+=2)assertEquals(NONE,alarm.update(t,true,false,20));
        assertEquals(NONE,alarm.update(1000,false,false,140));
        assertEquals(NONE,alarm.update(1002,true,true,140));
    }
    @Test void dryHotPlantWarnsOnceThenWaitsTwentySeconds(){
        var alarm=new PlantAlarm();assertEquals(WARNING,alarm.update(10,true,false,60));
        for(int tick=12;tick<410;tick+=2)assertEquals(NONE,alarm.update(tick,true,false,60));
        assertEquals(WARNING,alarm.update(410,true,false,60));
    }
    @Test void coolantFlickerDoesNotRetriggerAndDangerEscalates(){
        var alarm=new PlantAlarm();assertEquals(WARNING,alarm.update(0,true,false,46));
        assertEquals(NONE,alarm.update(2,true,true,46));assertEquals(NONE,alarm.update(4,true,false,46));
        assertEquals(URGENT,alarm.update(50,true,false,105));
        for(int tick=52;tick<150;tick+=2)assertEquals(NONE,alarm.update(tick,true,false,110));
        assertEquals(URGENT,alarm.update(150,true,false,110));
        assertEquals(NONE,alarm.update(250,true,true,110));
        assertEquals(NONE,alarm.update(650,true,false,37));
    }
}
