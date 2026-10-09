package com.example.myapplication;

import java.util.Calendar;
import java.util.TimeZone;

/** Filtros combináveis com limites de dia no fuso local, inclusive em mudanças de horário. */
public final class StatementQuery {
    public static final String ALL_CATEGORIES="Todas as categorias";
    public static final String[] PERIODS={"Todo o histórico","Hoje","Últimos 7 dias","Este mês","Intervalo personalizado"};
    public String search="", category=ALL_CATEGORIES, period=PERIODS[0];
    public long customStart=0, customEndExclusive=0;
    public boolean matches(String searchable,String entryCategory,long time,long now) {
        if (!category.equals(ALL_CATEGORIES) && !category.equals(entryCategory)) return false;
        String haystack=CategoryRules.normalize(searchable);
        for(String term:CategoryRules.normalize(search).split(" ")) if(!haystack.contains(term)) return false;
        Calendar start=Calendar.getInstance(); start.setTimeInMillis(now); midnight(start);
        long end=Long.MAX_VALUE;
        if(period.equals(PERIODS[1])) { Calendar next=(Calendar)start.clone();next.add(Calendar.DATE,1);midnight(next);end=next.getTimeInMillis(); }
        else if(period.equals(PERIODS[2])) { start.add(Calendar.DATE,-6);midnight(start);Calendar next=Calendar.getInstance();next.setTimeInMillis(now);midnight(next);next.add(Calendar.DATE,1);midnight(next);end=next.getTimeInMillis(); }
        else if(period.equals(PERIODS[3])) { start.set(Calendar.DAY_OF_MONTH,1);midnight(start);Calendar next=(Calendar)start.clone();next.add(Calendar.MONTH,1);midnight(next);end=next.getTimeInMillis(); }
        else if(period.equals(PERIODS[4])) return customEndExclusive>customStart && time>=customStart && time<customEndExclusive;
        else return true;
        return time>=start.getTimeInMillis() && time<end;
    }
    public static long dayStart(long time) {Calendar c=Calendar.getInstance();c.setTimeInMillis(time);midnight(c);return c.getTimeInMillis();}
    public static long nextDay(long start) {Calendar c=Calendar.getInstance();c.setTimeInMillis(start);c.add(Calendar.DATE,1);midnight(c);return c.getTimeInMillis();}
    public static long pickerDayToLocal(long utcDay) {
        Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.setTimeInMillis(utcDay);
        Calendar local=Calendar.getInstance();local.clear();local.set(utc.get(Calendar.YEAR),utc.get(Calendar.MONTH),utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }
    private static void midnight(Calendar c) {c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    public void clear() {search="";category=ALL_CATEGORIES;period=PERIODS[0];customStart=0;customEndExclusive=0;}
}
