package com.example.myapplication;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/** Agrupa pela data local de captura, com limites de mês exclusivos. */
public final class MonthlySummary {
    public final long start,end,previousStart;
    public final FinancialSummary current=new FinancialSummary(), previous=new FinancialSummary();
    public int count,previousCount,incompleteCount;
    private final Map<String,Long> categories=new LinkedHashMap<>();
    public MonthlySummary(long now,int monthOffset) {
        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.DAY_OF_MONTH,1);
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        c.add(Calendar.MONTH,monthOffset);start=c.getTimeInMillis();
        c.add(Calendar.MONTH,1);end=c.getTimeInMillis();
        c.add(Calendar.MONTH,-2);previousStart=c.getTimeInMillis();
    }
    public void add(long time,long cents,TransactionKind kind,boolean incomplete,String category) {
        if(time>=start && time<end) {
            count++;if(cents==0) incompleteCount++;
            current.add(cents,kind,incomplete);
            if(cents<0) categories.put(category,categories.getOrDefault(category,0L)-cents);
        } else if(time>=previousStart && time<start) {
            previousCount++;previous.add(cents,kind,incomplete);
        }
    }
    public ArrayList<Map.Entry<String,Long>> topCategories() {
        ArrayList<Map.Entry<String,Long>> rows=new ArrayList<>(categories.entrySet());
        rows.sort((a,b) -> {int result=Long.compare(b.getValue(),a.getValue());return result!=0 ? result : a.getKey().compareTo(b.getKey());});
        return rows;
    }
}
