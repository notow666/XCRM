package cn.cordys.crm.contract.service;

import cn.cordys.common.exception.GenericException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * 创收公式解析器：分词、调度场、逆波兰栈计算，全流程不使用递归和脚本引擎。
 */
@Service
public class RevenueFormulaEvaluator {

    private static final MathContext CALC_CONTEXT = new MathContext(24, RoundingMode.HALF_UP);
    private static final Set<String> VARIABLES = Set.of("放款金额", "回款金额", "成本金额", "杂费金额", "返佣金额");
    private static final Map<String, Integer> PRECEDENCE = Map.of(
            "+", 1, "-", 1, "*", 2, "/", 2, "u-", 3
    );

    public EvaluationResult evaluate(String formula, Map<String, BigDecimal> variableValues) {
        List<Token> tokens = tokenize(normalizeFormulaSymbols(formula));
        List<Token> rpn = toReversePolish(tokens);
        BigDecimal value = evaluateReversePolish(rpn, variableValues);
        String normalized = String.join(" ", tokens.stream().map(Token::text).toList());
        return new EvaluationResult(normalized, value.setScale(2, RoundingMode.HALF_UP));
    }

    private String normalizeFormulaSymbols(String formula) {
        if (formula == null) {
            return null;
        }
        return formula.replace('（', '(')
                .replace('）', ')')
                .replace('＋', '+')
                .replace('－', '-')
                .replace('＊', '*')
                .replace('／', '/');
    }

    private List<Token> tokenize(String formula) {
        if (StringUtils.isBlank(formula)) {
            throw new GenericException("创收公式不能为空");
        }
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        TokenType previousType = null;
        while (index < formula.length()) {
            char current = formula.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (Character.isDigit(current) || current == '.') {
                int start = index;
                int dotCount = 0;
                while (index < formula.length()) {
                    char numberChar = formula.charAt(index);
                    if (!Character.isDigit(numberChar) && numberChar != '.') {
                        break;
                    }
                    if (numberChar == '.') {
                        dotCount++;
                    }
                    index++;
                }
                String number = formula.substring(start, index);
                if (dotCount > 1 || ".".equals(number)) {
                    throw new GenericException("创收公式数字格式不合法");
                }
                try {
                    new BigDecimal(number);
                } catch (NumberFormatException e) {
                    throw new GenericException("创收公式数字格式不合法");
                }
                tokens.add(new Token(TokenType.NUMBER, number));
                previousType = TokenType.NUMBER;
                continue;
            }

            String variable = matchVariable(formula, index);
            if (variable != null) {
                tokens.add(new Token(TokenType.VARIABLE, variable));
                previousType = TokenType.VARIABLE;
                index += variable.length();
                continue;
            }
            if (current == '(') {
                tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                previousType = TokenType.LEFT_PAREN;
                index++;
                continue;
            }
            if (current == ')') {
                tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                previousType = TokenType.RIGHT_PAREN;
                index++;
                continue;
            }
            if (current == '+' || current == '-' || current == '*' || current == '/') {
                String operator = String.valueOf(current);
                boolean unary = current == '-' && (previousType == null
                        || previousType == TokenType.OPERATOR || previousType == TokenType.LEFT_PAREN);
                if (unary) {
                    operator = "u-";
                } else if (previousType == null || previousType == TokenType.OPERATOR
                        || previousType == TokenType.LEFT_PAREN) {
                    throw new GenericException("创收公式运算符位置不合法");
                }
                tokens.add(new Token(TokenType.OPERATOR, operator));
                previousType = TokenType.OPERATOR;
                index++;
                continue;
            }
            throw new GenericException("创收公式包含非法字符或变量");
        }
        if (tokens.isEmpty() || previousType == TokenType.OPERATOR || previousType == TokenType.LEFT_PAREN) {
            throw new GenericException("创收公式不完整");
        }
        return tokens;
    }

    private String matchVariable(String formula, int start) {
        for (String variable : VARIABLES) {
            if (formula.startsWith(variable, start)) {
                return variable;
            }
        }
        return null;
    }

    private List<Token> toReversePolish(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> operators = new ArrayDeque<>();
        TokenType previousType = null;
        for (Token token : tokens) {
            if ((token.type() == TokenType.NUMBER || token.type() == TokenType.VARIABLE)
                    && (previousType == TokenType.NUMBER || previousType == TokenType.VARIABLE
                    || previousType == TokenType.RIGHT_PAREN)) {
                throw new GenericException("创收公式缺少运算符");
            }
            if (token.type() == TokenType.LEFT_PAREN
                    && (previousType == TokenType.NUMBER || previousType == TokenType.VARIABLE
                    || previousType == TokenType.RIGHT_PAREN)) {
                throw new GenericException("创收公式不支持省略乘号");
            }
            if (token.type() == TokenType.NUMBER || token.type() == TokenType.VARIABLE) {
                output.add(token);
            } else if (token.type() == TokenType.OPERATOR) {
                while (!operators.isEmpty() && operators.peek().type() == TokenType.OPERATOR
                        && shouldPopOperator(token.text(), operators.peek().text())) {
                    output.add(operators.pop());
                }
                operators.push(token);
            } else if (token.type() == TokenType.LEFT_PAREN) {
                operators.push(token);
            } else {
                boolean foundLeftParenthesis = false;
                while (!operators.isEmpty()) {
                    Token operator = operators.pop();
                    if (operator.type() == TokenType.LEFT_PAREN) {
                        foundLeftParenthesis = true;
                        break;
                    }
                    output.add(operator);
                }
                if (!foundLeftParenthesis) {
                    throw new GenericException("创收公式括号不匹配");
                }
            }
            previousType = token.type();
        }
        while (!operators.isEmpty()) {
            Token operator = operators.pop();
            if (operator.type() == TokenType.LEFT_PAREN) {
                throw new GenericException("创收公式括号不匹配");
            }
            output.add(operator);
        }
        return output;
    }

    private boolean shouldPopOperator(String current, String top) {
        int currentPrecedence = PRECEDENCE.get(current);
        int topPrecedence = PRECEDENCE.get(top);
        boolean rightAssociative = "u-".equals(current);
        return rightAssociative ? currentPrecedence < topPrecedence : currentPrecedence <= topPrecedence;
    }

    private BigDecimal evaluateReversePolish(List<Token> rpn, Map<String, BigDecimal> variableValues) {
        Deque<BigDecimal> values = new ArrayDeque<>();
        for (Token token : rpn) {
            if (token.type() == TokenType.NUMBER) {
                values.push(new BigDecimal(token.text()));
                continue;
            }
            if (token.type() == TokenType.VARIABLE) {
                BigDecimal value = variableValues.get(token.text());
                if (value == null) {
                    throw new GenericException("创收公式变量缺少值：" + token.text());
                }
                values.push(value);
                continue;
            }
            if ("u-".equals(token.text())) {
                if (values.isEmpty()) {
                    throw new GenericException("创收公式缺少操作数");
                }
                values.push(values.pop().negate(CALC_CONTEXT));
                continue;
            }
            if (values.size() < 2) {
                throw new GenericException("创收公式缺少操作数");
            }
            BigDecimal right = values.pop();
            BigDecimal left = values.pop();
            values.push(calculate(left, right, token.text()));
        }
        if (values.size() != 1) {
            throw new GenericException("创收公式结构不合法");
        }
        return values.pop();
    }

    private BigDecimal calculate(BigDecimal left, BigDecimal right, String operator) {
        return switch (operator) {
            case "+" -> left.add(right, CALC_CONTEXT);
            case "-" -> left.subtract(right, CALC_CONTEXT);
            case "*" -> left.multiply(right, CALC_CONTEXT);
            case "/" -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new GenericException("创收公式除数不能为 0");
                }
                yield left.divide(right, 12, RoundingMode.HALF_UP);
            }
            default -> throw new GenericException("创收公式运算符不合法");
        };
    }

    private enum TokenType {
        NUMBER,
        VARIABLE,
        OPERATOR,
        LEFT_PAREN,
        RIGHT_PAREN
    }

    private record Token(TokenType type, String text) {
    }

    public record EvaluationResult(String normalizedFormula, BigDecimal value) {
    }
}
