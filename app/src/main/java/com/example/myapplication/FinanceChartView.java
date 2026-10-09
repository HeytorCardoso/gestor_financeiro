package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.text.NumberFormat;
import java.util.Locale;

/** Gráficos locais, sem dependências de rede. Toque seleciona um ponto e atualiza a legenda. */
public final class FinanceChartView extends View {
    public interface Selection {void selected(int index,String description);}
    public static final int BARS=0,DONUT=1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long[] first,second;
    private final String[] labels;
    private final int mode;
    private final Selection selection;
    private int selected=-1;
    private final int[] palette={0xff1a7053,0xffd5a24c,0xff8799b5,0xffbd674e,0xff8b75a5,0xff62a6a1,0xffa9ad70};
    private final NumberFormat money=NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    public FinanceChartView(Context context,int mode,String[] labels,long[] first,long[] second,Selection selection) {
        super(context);this.mode=mode;this.labels=labels;this.first=first;this.second=second;this.selection=selection;
        setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        StringBuilder accessible=new StringBuilder(mode==DONUT ? "Gastos por categoria. " : "Evolução financeira. ");
        for(int i=0;i<labels.length;i++)accessible.append(description(i)).append(". ");setContentDescription(accessible);
    }
    private float dp(float value) {return value*getResources().getDisplayMetrics().density;}
    private String description(int i) {
        return labels[i]+": "+(second==null ? money.format(first[i]/100.0) : "receitas "+money.format(first[i]/100.0)+", gastos "+money.format(second[i]/100.0));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(first.length==0)return;
        if(mode==DONUT) {drawDonut(canvas);return;}
        float left=dp(8),right=getWidth()-dp(8),top=dp(24),bottom=getHeight()-dp(28);
        double max=1,min=0;
        for(int i=0;i<first.length;i++) {max=Math.max(max,first[i]);min=Math.min(min,first[i]);if(second!=null)max=Math.max(max,second[i]);}
        paint.setColor(0xff728079);paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Escala: "+money.format(min/100.0)+" a "+money.format(max/100.0),left,dp(14),paint);
        float zero=(float)(top+max/(max-min)*(bottom-top));
        paint.setColor(0xffdce5df);paint.setStrokeWidth(dp(1));canvas.drawLine(left,zero,right,zero,paint);
        float slot=(right-left)/first.length;
        for(int i=0;i<first.length;i++) {
            float x=left+slot*i;int series=second==null ? 1 : 2;
            for(int j=0;j<series;j++) {
                long value=j==0 ? first[i] : second[i];float y=(float)(top+(max-value)/(max-min)*(bottom-top));
                float width=slot*(series==1 ? .65f : .34f);float start=x+slot*.14f+j*width;
                paint.setColor(j==1||value<0 ? 0xffbd674e : 0xff1a7053);paint.setAlpha(selected<0||selected==i ? 255 : 100);
                canvas.drawRoundRect(start,Math.min(y,zero),start+Math.max(1,width-dp(1)),Math.max(y,zero),dp(3),dp(3),paint);paint.setAlpha(255);
            }
            if(first.length<=6 || i==0 || (i+1)%5==0 || i==first.length-1) {
                paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xff728079);
                canvas.drawText(labels[i],x+slot/2,getHeight()-dp(8),paint);
            }
        }
    }
    private void drawDonut(Canvas canvas) {
        double total=0;for(long n:first)total+=n;if(total==0)return;
        float radius=Math.min(getWidth(),getHeight())*.36f,cx=getWidth()/2f,cy=getHeight()/2f;
        RectF bounds=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);float angle=-90;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(30));
        for(int i=0;i<first.length;i++) {
            float sweep=(float)(360.0*first[i]/total);paint.setColor(palette[i%palette.length]);paint.setAlpha(selected<0||selected==i ? 255 : 95);
            canvas.drawArc(bounds,angle,sweep,false,paint);angle+=sweep;
        }
        paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xff1a2d28);paint.setTextSize(dp(13));
        canvas.drawText("Gastos do mês",cx,cy-dp(6),paint);paint.setTextSize(dp(17));canvas.drawText(money.format(total/100),cx,cy+dp(18),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_UP && first.length>0) {
            if(mode==DONUT) {
                double total=0;for(long n:first)total+=n;
                double angle=(Math.toDegrees(Math.atan2(event.getY()-getHeight()/2.0,event.getX()-getWidth()/2.0))+450)%360,edge=0;
                selected=first.length-1;
                for(int i=0;i<first.length;i++) {edge+=360.0*first[i]/total;if(angle<edge){selected=i;break;}}
            } else selected=Math.max(0,Math.min(first.length-1,(int)((event.getX()-dp(8))/(getWidth()-dp(16))*first.length)));
            performClick();return true;
        }return true;
    }
    @Override public boolean onKeyDown(int keyCode,android.view.KeyEvent event) {
        if(first.length>0 && (keyCode==android.view.KeyEvent.KEYCODE_DPAD_LEFT || keyCode==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
            selected=Math.max(0,Math.min(first.length-1,selected+(keyCode==android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
            performClick();return true;
        }return super.onKeyDown(keyCode,event);
    }
    @Override public boolean performClick() {
        super.performClick();if(first.length==0)return true;if(selected<0)selected=0;
        if(selection!=null)selection.selected(selected,description(selected));invalidate();return true;
    }
}
