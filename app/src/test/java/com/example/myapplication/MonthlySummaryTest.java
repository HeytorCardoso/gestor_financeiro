package com.example.myapplication;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class MonthlySummaryTest {
    private TimeZone previous;
    @Before public void before() {previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));}
    @After public void after() {TimeZone.setDefault(previous);}
    private long time(int year,int month,int day) {Calendar c=Calendar.getInstance();c.clear();c.set(year,month-1,day);return c.getTimeInMillis();}
    @Test public void boundariesIncludeStartAndExcludeNextMonth() {
        MonthlySummary m=new MonthlySummary(time(2026,10,31),0);
        m.add(time(2026,10,1),100,TransactionKind.ACCOUNT_TRANSFER,false,"Outros");
        m.add(time(2026,11,1),900,TransactionKind.ACCOUNT_TRANSFER,false,"Outros");
        m.add(time(2026,9,30),200,TransactionKind.ACCOUNT_TRANSFER,false,"Outros");
        m.add(time(2026,8,31),800,TransactionKind.ACCOUNT_TRANSFER,false,"Outros");
        assertEquals(100,m.current.receipts);assertEquals(200,m.previous.receipts);assertEquals(1,m.count);assertEquals(1,m.previousCount);
    }
    @Test public void offsetCrossesYearAndHandlesLeapFebruary() {
        MonthlySummary m=new MonthlySummary(time(2024,3,31),-1);
        assertEquals(time(2024,2,1),m.start);assertEquals(time(2024,3,1),m.end);
        m.add(time(2024,2,29),-100,TransactionKind.UNKNOWN,false,"Outros");assertEquals(100,m.current.expenses);
        MonthlySummary january=new MonthlySummary(time(2026,1,31),0);
        assertEquals(time(2025,12,1),january.previousStart);
    }
    @Test public void invoicesAndUnclassifiedCountAndCategoriesSortBySpending() {
        long now=time(2026,10,8);MonthlySummary m=new MonthlySummary(now,0);
        m.add(now,-300,TransactionKind.INVOICE_PAYMENT,false,"Casa");
        m.add(now,-500,TransactionKind.UNKNOWN,true,"Outros");
        m.add(now,-200,TransactionKind.CREDIT_PURCHASE,false,"Casa");
        m.add(now,1000,TransactionKind.ACCOUNT_TRANSFER,false,"Trabalho");
        m.add(now,0,TransactionKind.UNKNOWN,true,"Outros");
        assertEquals(1000,m.current.expenses);assertEquals(0,m.current.result());assertEquals(1,m.incompleteCount);
        assertEquals(2,m.topCategories().size());assertEquals("Casa",m.topCategories().get(0).getKey());assertEquals(Long.valueOf(500),m.topCategories().get(1).getValue());
    }
    @Test public void emptyMonthHasNoCategoriesOrComparisonRecords() {
        MonthlySummary m=new MonthlySummary(time(2026,10,8),0);
        assertEquals(0,m.current.result());assertEquals(0,m.previousCount);assertTrue(m.topCategories().isEmpty());
    }
    @Test public void daylightSavingDoesNotShiftMonthBoundary() {
        MonthlySummary m=new MonthlySummary(time(2018,11,15),0);
        assertEquals(time(2018,11,1),m.start);assertEquals(time(2018,12,1),m.end);
    }
}
