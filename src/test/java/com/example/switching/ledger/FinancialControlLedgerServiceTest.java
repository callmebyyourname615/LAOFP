package com.example.switching.ledger;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FinancialControlLedgerServiceTest {
 @Test void acceptsBalancedLines(){assertDoesNotThrow(()->FinancialControlLedgerService.validateBalanced(List.of(new LedgerLine("A", LedgerLine.Side.DEBIT,new BigDecimal("10"),null),new LedgerLine("B", LedgerLine.Side.CREDIT,new BigDecimal("10.00"),null))));}
 @Test void rejectsUnbalancedLines(){assertThrows(IllegalArgumentException.class,()->FinancialControlLedgerService.validateBalanced(List.of(new LedgerLine("A", LedgerLine.Side.DEBIT,new BigDecimal("10"),null),new LedgerLine("B", LedgerLine.Side.CREDIT,new BigDecimal("9"),null))));}
}
