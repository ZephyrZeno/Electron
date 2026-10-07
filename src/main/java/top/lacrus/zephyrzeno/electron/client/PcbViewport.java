package top.lacrus.zephyrzeno.electron.client;

/** Canvas coordinates only: UI scaling and inventory coordinates stay outside this transform. */
public final class PcbViewport {
    public static final int WIDTH=272, HEIGHT=211;
    private int columns,rows,dragButton=-1;
    private double zoom=1,panX,panY,lastX,lastY;

    public void boardSize(int columns,int rows){
        if(this.columns==columns&&this.rows==rows)return;
        this.columns=columns;this.rows=rows;fit();
    }
    public void fit(){
        if(columns<=0||rows<=0)return;
        zoom=Math.min((WIDTH-22d)/columns,(HEIGHT-21d)/rows);
        panX=(WIDTH-(columns-1)*zoom)/2;panY=(HEIGHT-(rows-1)*zoom)/2;
        cancelPan();
    }
    public double zoom(){return zoom;}
    public double screenX(double column){return panX+column*zoom;}
    public double screenY(double row){return panY+row*zoom;}
    public double column(double x){return (x-panX)/zoom;}
    public double row(double y){return (y-panY)/zoom;}
    public void pan(double dx,double dy){
        // Keep a small part of the board reachable, even after dragging out of the canvas.
        panX=Math.clamp(panX+dx,24-(columns-.5)*zoom,WIDTH-24+zoom/2);
        panY=Math.clamp(panY+dy,24-(rows-.5)*zoom,HEIGHT-24+zoom/2);
    }
    public void zoomAt(double x,double y,double steps){
        double column=column(x),row=row(y);
        zoom=Math.clamp(zoom*Math.pow(1.18,steps),.75,40);
        panX=x-column*zoom;panY=y-row*zoom;
    }
    public void beginPan(int button,double x,double y){dragButton=button;lastX=x;lastY=y;}
    public boolean dragging(){return dragButton>=0;}
    public boolean drag(int button,double x,double y){
        if(button!=dragButton)return false;
        pan(x-lastX,y-lastY);lastX=x;lastY=y;return true;
    }
    public boolean endPan(int button){if(button!=dragButton)return false;cancelPan();return true;}
    public void cancelPan(){dragButton=-1;}
}
