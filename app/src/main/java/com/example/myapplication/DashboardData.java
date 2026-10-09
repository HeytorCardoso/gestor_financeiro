package com.example.myapplication;

import java.util.Calendar;

/** Séries em centavos; recebe somente os registros ativos do histórico. */
public final class DashboardData {
    public final MonthlySummary month;
    public final long[] dailyIncome,dailyExpenses,monthlyResults=new long[6];
    public final long[] monthStarts=new long[6];
    private final MonthlySummary[] months=new MonthlySummary[6];
    public DashboardData(long now,int offset) {
        month=new MonthlySummary(now,offset);
        Calendar c=Calendar.getInstance();c.setTimeInMillis(month.start);
        dailyIncome=new long[c.getActualMaximum(Calendar.DAY_OF_MONTH)];dailyExpenses=new long[dailyIncome.length];
        for(int i=0;i<6;i++) {months[i]=new MonthlySummary(now,offset-5+i);monthStarts[i]=months[i].start;}
    }
    public void add(long time,long cents,TransactionKind kind,boolean incomplete,String category) {
        month.add(time,cents,kind,incomplete,category);
        if(time>=month.start && time<month.end) {
            Calendar c=Calendar.getInstance();c.setTimeInMillis(time);int day=c.get(Calendar.DAY_OF_MONTH)-1;
            if(cents>0)dailyIncome[day]+=cents;else if(cents<0)dailyExpenses[day]-=cents;
        }
        for(int i=0;i<6;i++) {
            months[i].add(time,cents,kind,incomplete,category);monthlyResults[i]=months[i].current.result();
        }
    }
}
