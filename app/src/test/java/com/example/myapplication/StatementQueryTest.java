package com.example.myapplication;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class StatementQueryTest {
    private TimeZone previous;
    @Before public void before() {previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));}
    @After public void after() {TimeZone.setDefault(previous);}
    private long time(int year,int month,int day,int hour,int minute) {Calendar c=Calendar.getInstance();c.clear();c.set(year,month-1,day,hour,minute);return c.getTimeInMillis();}
    @Test public void searchIgnoresAccentsAndCombinesWordsWithCategory() {
        StatementQuery q=new StatementQuery();q.search="cafe manhã";q.category="Alimentação";
        assertTrue(q.matches("Café da manhã em Padaria","Alimentação",0,0));
        assertFalse(q.matches("Café da tarde","Alimentação",0,0));
        assertFalse(q.matches("Café da manhã","Casa",0,0));
    }
    @Test public void todayIncludesWholeLocalDayOnly() {
        StatementQuery q=new StatementQuery();q.period=StatementQuery.PERIODS[1];long now=time(2026,10,8,12,0);
        assertTrue(q.matches("","Outros",time(2026,10,8,0,0),now));
        assertTrue(q.matches("","Outros",time(2026,10,8,23,59),now));
        assertFalse(q.matches("","Outros",time(2026,10,9,0,0),now));
    }
    @Test public void lastSevenDaysIncludesTodayAndSixPreviousDays() {
        StatementQuery q=new StatementQuery();q.period=StatementQuery.PERIODS[2];long now=time(2026,10,8,12,0);
        assertTrue(q.matches("","Outros",time(2026,10,2,0,0),now));
        assertFalse(q.matches("","Outros",time(2026,10,1,23,59),now));
    }
    @Test public void currentMonthStopsAtNextMonth() {
        StatementQuery q=new StatementQuery();q.period=StatementQuery.PERIODS[3];long now=time(2026,10,8,12,0);
        assertTrue(q.matches("","Outros",time(2026,10,1,0,0),now));
        assertFalse(q.matches("","Outros",time(2026,11,1,0,0),now));
    }
    @Test public void customRangeIncludesLastDayWithoutLeakingIntoNext() {
        StatementQuery q=new StatementQuery();q.period=StatementQuery.PERIODS[4];
        q.customStart=time(2026,10,1,0,0);q.customEndExclusive=time(2026,10,4,0,0);
        assertTrue(q.matches("","Outros",time(2026,10,3,23,59),0));
        assertFalse(q.matches("","Outros",time(2026,10,4,0,0),0));
    }
    @Test public void pickerUtcDayDoesNotBecomePreviousLocalDay() {
        Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.clear();utc.set(2026,9,8);
        assertEquals(time(2026,10,8,0,0),StatementQuery.pickerDayToLocal(utc.getTimeInMillis()));
    }
    @Test public void historicalDaylightSavingDoesNotExtendIntoFollowingDay() {
        long start=StatementQuery.dayStart(time(2018,11,4,12,0));
        assertEquals(time(2018,11,5,0,0),StatementQuery.nextDay(start));
        StatementQuery q=new StatementQuery();q.period=StatementQuery.PERIODS[1];
        assertFalse(q.matches("","Outros",time(2018,11,5,0,30),time(2018,11,4,12,0)));
    }
    @Test public void clearRestoresAllFilters() {
        StatementQuery q=new StatementQuery();q.search="Cafe";q.category="Casa";q.period=StatementQuery.PERIODS[4];q.customStart=1;q.customEndExclusive=2;
        q.clear();assertTrue(q.matches("Loja","Outros",0,0));assertEquals(0,q.customEndExclusive);
    }
}
