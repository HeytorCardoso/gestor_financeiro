package com.example.myapplication;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class DashboardDataTest {
    private TimeZone previous;
    @Before public void before() {previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));}
    @After public void after() {TimeZone.setDefault(previous);}
    private long time(int year,int month,int day) {Calendar c=Calendar.getInstance();c.clear();c.set(year,month-1,day);return c.getTimeInMillis();}
    @Test public void dailySeriesIncludeInvoicesAndUnclassifiedAndReconcileTotals() {
        long now=time(2026,10,8);DashboardData d=new DashboardData(now,0);
        d.add(now,10000,TransactionKind.ACCOUNT_TRANSFER,false,"Trabalho");
        d.add(now,-2000,TransactionKind.INVOICE_PAYMENT,false,"Outros");
        d.add(time(2026,10,31),-1500,TransactionKind.UNKNOWN,false,"Casa");
        d.add(now,0,TransactionKind.UNKNOWN,true,"Outros");
        d.add(time(2026,11,1),-9900,TransactionKind.CREDIT_PURCHASE,false,"Casa");
        long receipts=0,expenses=0;for(long v:d.dailyIncome)receipts+=v;for(long v:d.dailyExpenses)expenses+=v;
        assertEquals(10000,d.dailyIncome[7]);assertEquals(1500,d.dailyExpenses[30]);
        assertEquals(d.month.current.receipts,receipts);assertEquals(d.month.current.expenses,expenses);
        assertEquals(3500,expenses);assertEquals(6500,d.monthlyResults[5]);assertEquals(1,d.month.incompleteCount);
    }
    @Test public void sixMonthsCrossYearAndUseOnlyTheirOwnRecords() {
        DashboardData d=new DashboardData(time(2026,3,15),0);
        assertEquals(time(2025,10,1),d.monthStarts[0]);
        d.add(time(2025,10,1),1000,TransactionKind.UNKNOWN,false,"Outros");
        d.add(time(2025,9,30),9000,TransactionKind.UNKNOWN,false,"Outros");
        d.add(time(2026,2,28),-700,TransactionKind.INVOICE_PAYMENT,false,"Outros");
        assertEquals(1000,d.monthlyResults[0]);assertEquals(-700,d.monthlyResults[4]);assertEquals(0,d.monthlyResults[5]);
    }
    @Test public void monthOffsetAndLeapFebruaryHaveCorrectDayCount() {
        DashboardData d=new DashboardData(time(2024,3,31),-1);
        assertEquals(29,d.dailyIncome.length);
        d.add(time(2024,2,29),-100,TransactionKind.UNKNOWN,false,"Outros");assertEquals(100,d.dailyExpenses[28]);
    }
    @Test public void emptySeriesHaveZeroValues() {
        DashboardData d=new DashboardData(time(2026,10,8),0);
        assertEquals(0,d.month.count);for(long n:d.monthlyResults)assertEquals(0,n);assertTrue(d.month.topCategories().isEmpty());
    }
}
