package cn.cordys.crm.contract.service;

import cn.cordys.common.exception.GenericException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RevenueFormulaEvaluatorTest {

    private RevenueFormulaEvaluator evaluator;
    private Map<String, BigDecimal> variables;

    @BeforeEach
    void setUp() {
        evaluator = new RevenueFormulaEvaluator();
        variables = Map.of(
                "放款金额", new BigDecimal("1000"),
                "回款金额", new BigDecimal("800"),
                "成本金额", new BigDecimal("120"),
                "杂费金额", new BigDecimal("30"),
                "返佣金额", new BigDecimal("50")
        );
    }

    @Test
    void shouldRespectParenthesesAndOperatorPrecedence() {
        RevenueFormulaEvaluator.EvaluationResult result =
                evaluator.evaluate("(回款金额 - 成本金额 - 杂费金额) * 0.8", variables);

        assertEquals(new BigDecimal("520.00"), result.value());
        assertEquals("( 回款金额 - 成本金额 - 杂费金额 ) * 0.8", result.normalizedFormula());
    }

    @Test
    void shouldSupportUnaryMinusAndRoundToTwoDecimals() {
        RevenueFormulaEvaluator.EvaluationResult result =
                evaluator.evaluate("-返佣金额 + 放款金额 / 3", variables);

        assertEquals(new BigDecimal("283.33"), result.value());
    }

    @Test
    void shouldNormalizeFullWidthOperatorsAndParentheses() {
        RevenueFormulaEvaluator.EvaluationResult result =
                evaluator.evaluate("（回款金额 － 成本金额 － 杂费金额）＊ 0.8", variables);

        assertEquals(new BigDecimal("520.00"), result.value());
        assertEquals("( 回款金额 - 成本金额 - 杂费金额 ) * 0.8", result.normalizedFormula());
    }

    @Test
    void shouldRejectUnknownVariable() {
        assertThrows(GenericException.class, () -> evaluator.evaluate("未知金额 + 1", variables));
    }

    @Test
    void shouldRejectMissingVariableValue() {
        assertThrows(GenericException.class,
                () -> evaluator.evaluate("回款金额 - 成本金额", Map.of("回款金额", BigDecimal.TEN)));
    }

    @Test
    void shouldRejectDivisionByZero() {
        assertThrows(GenericException.class, () -> evaluator.evaluate("回款金额 / (成本金额 - 120)", variables));
    }

    @Test
    void shouldRejectMismatchedParentheses() {
        assertThrows(GenericException.class, () -> evaluator.evaluate("(回款金额 + 1", variables));
    }
}
